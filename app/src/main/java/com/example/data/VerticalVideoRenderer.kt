package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.model.CaptionSegment
import com.example.model.CaptionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "VerticalVideoRenderer"
private const val OUT_WIDTH = 1080
private const val OUT_HEIGHT = 1920

object VerticalVideoRenderer {

    @OptIn(UnstableApi::class)
    suspend fun renderVerticalClip(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startSec: Double,
        durationSec: Int,
        verticalBlur: Boolean,
        enableCaptions: Boolean,
        captionStyle: CaptionStyle,
        captions: List<CaptionSegment>,
        enableHook: Boolean,
        hookText: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val sourceDims = extractSourceVideoInfo(context, inputUri, startSec)
        val blurredBg = if (verticalBlur) {
            sourceDims.sampleFrame?.let { createVerticalBlurredBackground(it) }
        } else null

        val fgRect = computeCenteredVideoRect(sourceDims.width, sourceDims.height)
        val typeface = runCatching {
            ResourcesCompat.getFont(context, R.font.space_grotesk)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD

        val overlay = ViralClipCanvasOverlay(
            blurredBackground = blurredBg,
            foregroundVideoRect = fgRect,
            verticalBlur = verticalBlur,
            enableCaptions = enableCaptions,
            captionStyle = captionStyle,
            captions = captions,
            enableHook = enableHook,
            hookText = hookText.trim(),
            headlineTypeface = typeface
        )

        val startMs = (startSec * 1000.0).toLong().coerceAtLeast(0L)
        val endMs = startMs + durationSec * 1000L

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val presentation = if (verticalBlur) {
            Presentation.createForWidthAndHeight(
                OUT_WIDTH,
                OUT_HEIGHT,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        } else {
            Presentation.createForWidthAndHeight(
                OUT_WIDTH,
                OUT_HEIGHT,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        }

        val videoEffects = mutableListOf<Effect>(presentation)
        if (verticalBlur || (enableHook && hookText.isNotBlank()) || (enableCaptions && captions.isNotEmpty())) {
            videoEffects.add(OverlayEffect(listOf(overlay)))
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        runTransformerOnMainThread(context, editedMediaItem, outputFile, onProgress)
        blurredBg?.recycle()
        outputFile
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMainThread(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var transformer: Transformer? = null
            var polling = true

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!polling) return
                    val t = transformer ?: return
                    val state = t.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && polling) {
                        handler.postDelayed(this, 250L)
                    }
                }
            }

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    polling = false
                    handler.removeCallbacks(pollRunnable)
                    onProgress(1f)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    polling = false
                    handler.removeCallbacks(pollRunnable)
                    Log.e(TAG, "Media3 Transformer export failed", exportException)
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }

            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            continuation.invokeOnCancellation {
                polling = false
                handler.removeCallbacks(pollRunnable)
                runCatching { transformer.cancel() }
            }

            transformer.start(editedMediaItem, outputFile.absolutePath)
            handler.postDelayed(pollRunnable, 250L)
        }
    }

    private data class SourceVideoFrameInfo(
        val width: Int,
        val height: Int,
        val sampleFrame: Bitmap?
    )

    private fun extractSourceVideoInfo(
        context: Context,
        uri: Uri,
        startSec: Double
    ): SourceVideoFrameInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
            val frame = retriever.getFrameAtTime(
                ((startSec + 1.0) * 1_000_000.0).toLong().coerceAtLeast(0L),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(0L)
            SourceVideoFrameInfo(w, h, frame)
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting source frame info", e)
            SourceVideoFrameInfo(1920, 1080, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun computeCenteredVideoRect(srcW: Int, srcH: Int): Rect {
        val srcAspect = srcW.toFloat() / max(1, srcH).toFloat()
        val dstAspect = OUT_WIDTH.toFloat() / OUT_HEIGHT.toFloat()
        return if (srcAspect > dstAspect) {
            val scaledH = (OUT_WIDTH / srcAspect).roundToInt().coerceIn(1, OUT_HEIGHT)
            val top = (OUT_HEIGHT - scaledH) / 2
            Rect(0, top, OUT_WIDTH, top + scaledH)
        } else {
            val scaledW = (OUT_HEIGHT * srcAspect).roundToInt().coerceIn(1, OUT_WIDTH)
            val left = (OUT_WIDTH - scaledW) / 2
            Rect(left, 0, left + scaledW, OUT_HEIGHT)
        }
    }

    /**
     * Downscales + runs a fast multi-pass box blur + scales up with bilinear filtering
     * to produce a smooth 1080x1920 vertical background frame compatible with all API 24+ devices.
     */
    private fun createVerticalBlurredBackground(sourceFrame: Bitmap): Bitmap {
        val smallW = 108
        val smallH = 192
        val srcRatio = sourceFrame.width.toFloat() / max(1, sourceFrame.height)
        val targetRatio = smallW.toFloat() / smallH.toFloat()

        val cropRect = if (srcRatio > targetRatio) {
            val cropW = (sourceFrame.height * targetRatio).roundToInt().coerceIn(1, sourceFrame.width)
            val left = (sourceFrame.width - cropW) / 2
            Rect(left, 0, left + cropW, sourceFrame.height)
        } else {
            val cropH = (sourceFrame.width / targetRatio).roundToInt().coerceIn(1, sourceFrame.height)
            val top = (sourceFrame.height - cropH) / 2
            Rect(0, top, sourceFrame.width, top + cropH)
        }

        val tiny = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        val tinyCanvas = Canvas(tiny)
        tinyCanvas.drawBitmap(sourceFrame, cropRect, Rect(0, 0, smallW, smallH), Paint(Paint.FILTER_BITMAP_FLAG))

        val blurredTiny = fastBoxBlur(tiny, radius = 8)
        tiny.recycle()

        val full = Bitmap.createScaledBitmap(blurredTiny, OUT_WIDTH, OUT_HEIGHT, true)
        blurredTiny.recycle()

        val dimCanvas = Canvas(full)
        dimCanvas.drawColor(Color.argb(105, 10, 8, 20))
        return full
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val div = radius * 2 + 1

        repeat(2) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (dx in -radius..radius) {
                        val sx = (x + dx).coerceIn(0, w - 1)
                        val c = pix[rowOff + sx]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                    }
                    tmp[rowOff + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (dy in -radius..radius) {
                        val sy = (y + dy).coerceIn(0, h - 1)
                        val c = tmp[sy * w + x]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                    }
                    pix[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                }
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    @OptIn(UnstableApi::class)
    private class ViralClipCanvasOverlay(
        private val blurredBackground: Bitmap?,
        private val foregroundVideoRect: Rect,
        private val verticalBlur: Boolean,
        private val enableCaptions: Boolean,
        private val captionStyle: CaptionStyle,
        private val captions: List<CaptionSegment>,
        private val enableHook: Boolean,
        private val hookText: String,
        headlineTypeface: Typeface
    ) : CanvasOverlay(/* useInputFrameSize = */ true) {

        private val hookFillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 60f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        private val hookBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 12, 10, 24)
            style = Paint.Style.FILL
        }

        private val hookBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 139, 92, 246)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val captionStrokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 16f
            strokeJoin = Paint.Join.ROUND
        }

        private val captionFillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = captionStyle.primaryColorInt
            textSize = 72f
            typeface = Typeface.create(headlineTypeface, Typeface.BOLD)
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 6f, Color.argb(190, 0, 0, 0))
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val w = canvas.width
            val h = canvas.height

            // 1. Blurred letterbox/pillarbox regions (leaving centered video window completely transparent)
            if (verticalBlur && blurredBackground != null) {
                if (foregroundVideoRect.top > 0) {
                    val topSrc = Rect(0, 0, OUT_WIDTH, foregroundVideoRect.top)
                    val topDst = Rect(0, 0, w, (foregroundVideoRect.top * h / OUT_HEIGHT.toFloat()).roundToInt())
                    canvas.drawBitmap(blurredBackground, topSrc, topDst, null)
                }
                if (foregroundVideoRect.bottom < OUT_HEIGHT) {
                    val botSrc = Rect(0, foregroundVideoRect.bottom, OUT_WIDTH, OUT_HEIGHT)
                    val botDst = Rect(0, (foregroundVideoRect.bottom * h / OUT_HEIGHT.toFloat()).roundToInt(), w, h)
                    canvas.drawBitmap(blurredBackground, botSrc, botDst, null)
                }
            }

            // 2. Top Hook Header Pill
            if (enableHook && hookText.isNotBlank()) {
                val maxTextWidth = (w * 0.82f).roundToInt()
                val layout = StaticLayout.Builder.obtain(
                    hookText.uppercase(),
                    0,
                    hookText.length,
                    hookFillPaint,
                    maxTextWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(6f, 1f)
                    .build()

                val padX = 44f
                val padY = 26f
                val textWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: maxTextWidth.toFloat()
                val boxLeft = (w - textWidth) / 2f - padX
                val boxRight = (w + textWidth) / 2f + padX
                val boxTop = h * 0.105f
                val boxBottom = boxTop + layout.height + padY * 2f

                val pillRect = RectF(boxLeft, boxTop, boxRight, boxBottom)
                canvas.drawRoundRect(pillRect, 28f, 28f, hookBoxPaint)
                canvas.drawRoundRect(pillRect, 28f, 28f, hookBorderPaint)

                canvas.save()
                canvas.translate((w - maxTextWidth) / 2f, boxTop + padY)
                layout.draw(canvas)
                canvas.restore()
            }

            // 3. Dynamic Synced Auto-Captions
            if (enableCaptions && captions.isNotEmpty()) {
                val currentSec = presentationTimeUs / 1_000_000.0
                val active = captions.firstOrNull { currentSec in it.startSec..it.endSec }
                if (active != null) {
                    val maxWidth = (w * 0.86f).roundToInt()
                    val strokeLayout = StaticLayout.Builder.obtain(
                        active.text,
                        0,
                        active.text.length,
                        captionStrokePaint,
                        maxWidth
                    )
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(6f, 1f)
                        .build()

                    val fillLayout = StaticLayout.Builder.obtain(
                        active.text,
                        0,
                        active.text.length,
                        captionFillPaint,
                        maxWidth
                    )
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(6f, 1f)
                        .build()

                    val yPos = h * 0.75f - strokeLayout.height / 2f
                    canvas.save()
                    canvas.translate((w - maxWidth) / 2f, yPos)
                    strokeLayout.draw(canvas)
                    fillLayout.draw(canvas)
                    canvas.restore()
                }
            }
        }
    }
}

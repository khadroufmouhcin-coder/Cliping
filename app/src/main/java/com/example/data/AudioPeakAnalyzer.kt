package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.AudioEnergyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "AudioPeakAnalyzer"
private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024 // Keep well under 20MB Gemini inline limit

data class VideoMediaMetadata(
    val displayName: String,
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap?
)

object AudioPeakAnalyzer {

    suspend fun getVideoMetadata(context: Context, uri: Uri): VideoMediaMetadata =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 30_000L
                val width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 1920
                val height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 1080
                val thumb = retriever.getFrameAtTime(
                    min(durationMs * 1000L / 4L, 2_000_000L),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                val name = resolveDisplayName(context, uri) ?: "Selected Video"
                VideoMediaMetadata(
                    displayName = name,
                    durationSec = durationMs / 1000.0,
                    width = width,
                    height = height,
                    thumbnail = thumb
                )
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    /**
     * Ports the Colab RMS peak detector to Android using MediaExtractor + MediaCodec PCM decoding.
     * Computes 1-second RMS energy buckets and starts the viral clip 5 seconds before the peak window.
     */
    suspend fun analyzeAudioEnergy(
        context: Context,
        uri: Uri,
        clipDurationSec: Int
    ): AudioEnergyProfile = withContext(Dispatchers.IO) {
        val meta = getVideoMetadata(context, uri)
        val totalSecInt = max(1, meta.durationSec.toInt())
        if (meta.durationSec <= clipDurationSec) {
            val flat = List(totalSecInt) { 0.5f }
            return@withContext AudioEnergyProfile(meta.durationSec, flat, 0.0)
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext AudioEnergyProfile(
                    durationSec = meta.durationSec,
                    secondEnergies = List(totalSecInt) { 0.25f },
                    peakStartSec = 0.0
                )
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bucketSquares = DoubleArray(totalSecInt)
            val bucketCounts = LongArray(totalSecInt)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 5_000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val secIndex = ((bufferInfo.presentationTimeUs / 1_000_000L).toInt())
                            .coerceIn(0, totalSecInt - 1)
                        accumulatePcmEnergy(
                            buffer = outBuf,
                            offset = bufferInfo.offset,
                            sizeBytes = bufferInfo.size,
                            channels = channels,
                            sampleRate = sampleRate,
                            startSecIndex = secIndex,
                            bucketSquares = bucketSquares,
                            bucketCounts = bucketCounts
                        )
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            val rawRms = DoubleArray(totalSecInt) { i ->
                if (bucketCounts[i] > 0) sqrt(bucketSquares[i] / bucketCounts[i]) else 0.0
            }
            val maxRms = rawRms.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
            val normalized = rawRms.map { (it / maxRms).toFloat().coerceIn(0f, 1f) }

            var bestStart = 0
            var bestMean = -1.0
            val windowEndLimit = max(1, rawRms.size - clipDurationSec)
            for (s in 0 until windowEndLimit) {
                val end = min(rawRms.size, s + clipDurationSec)
                var sum = 0.0
                for (k in s until end) sum += rawRms[k]
                val mean = sum / max(1, end - s)
                if (mean > bestMean) {
                    bestMean = mean
                    bestStart = s
                }
            }

            val peakStart = max(0.0, (bestStart - 5).toDouble())
                .coerceAtMost(max(0.0, meta.durationSec - clipDurationSec))

            AudioEnergyProfile(
                durationSec = meta.durationSec,
                secondEnergies = normalized,
                peakStartSec = peakStart
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio RMS analysis failed, falling back to start=0", e)
            AudioEnergyProfile(
                durationSec = meta.durationSec,
                secondEnergies = List(totalSecInt) { 0.35f },
                peakStartSec = 0.0
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun accumulatePcmEnergy(
        buffer: ByteBuffer,
        offset: Int,
        sizeBytes: Int,
        channels: Int,
        sampleRate: Int,
        startSecIndex: Int,
        bucketSquares: DoubleArray,
        bucketCounts: LongArray
    ) {
        val dup = buffer.duplicate()
        dup.position(offset)
        dup.limit(offset + sizeBytes)
        val samplesTotal = sizeBytes / 2
        val stride = max(1, channels)
        var frameIdx = 0
        var i = 0
        while (i + 1 < sizeBytes) {
            val lo = dup.get().toInt() and 0xFF
            val hi = dup.get().toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
            if ((frameIdx % stride) == 0) {
                val secOffset = if (sampleRate > 0) (frameIdx / stride) / sampleRate else 0
                val idx = (startSecIndex + secOffset).coerceIn(0, bucketSquares.lastIndex)
                bucketSquares[idx] += sample * sample
                bucketCounts[idx]++
            }
            frameIdx++
            i += 2
        }
    }

    /**
     * Extracts ONLY the clip window's compressed audio track as an M4A byte array (~200-500 KB)
     * so Gemini 3.8 Flash can transcribe exact timestamps without exceeding payload limits.
     */
    suspend fun extractAudioClipBytes(
        context: Context,
        uri: Uri,
        startSec: Double,
        durationSec: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "clip_audio_${System.currentTimeMillis()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioIdx = i
                    format = f
                    break
                }
            }
            if (audioIdx == -1 || format == null) return@withContext null

            val startUs = (startSec * 1_000_000.0).toLong().coerceAtLeast(0L)
            val endUs = startUs + durationSec * 1_000_000L

            extractor.selectTrack(audioIdx)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(format)
            muxer.start()

            val buf = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstPts = -1L

            while (true) {
                val sampleSize = extractor.readSampleData(buf, 0)
                if (sampleSize < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                if (pts >= startUs) {
                    if (firstPts < 0L) firstPts = pts
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (pts - firstPts).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buf, info)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            if (!tempFile.exists() || tempFile.length() <= 0L || tempFile.length() > MAX_INLINE_AUDIO_BYTES) {
                return@withContext null
            }
            tempFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Audio clip extraction failed", e)
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    suspend fun extractKeyFrames(
        context: Context,
        uri: Uri,
        startSec: Double,
        durationSec: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val points = listOf(0.15, 0.5, 0.85)
            points.mapNotNull { ratio ->
                val us = ((startSec + durationSec * ratio) * 1_000_000.0).toLong()
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { bmp ->
                    val maxDim = 512
                    val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
                    if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bmp,
                            max(1, (bmp.width * scale).toInt()),
                            max(1, (bmp.height * scale).toInt()),
                            true
                        )
                    } else bmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keyframe extraction failed", e)
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }
}

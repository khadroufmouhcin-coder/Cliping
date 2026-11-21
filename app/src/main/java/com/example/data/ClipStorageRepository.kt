package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.CaptionSegment
import com.example.model.CaptionStyle
import com.example.model.SavedClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ClipStorageRepository {

    private const val CLIPS_DIR = "viral_clips"

    fun clipsDirectory(context: Context): File {
        return File(context.filesDir, CLIPS_DIR).apply { mkdirs() }
    }

    fun newClipOutputFile(context: Context): Pair<String, File> {
        val id = UUID.randomUUID().toString().replace("-", "").take(10)
        val file = File(clipsDirectory(context), "viral_$id.mp4")
        return id to file
    }

    suspend fun saveClipMetadata(context: Context, clip: SavedClip) = withContext(Dispatchers.IO) {
        val metaFile = File(clipsDirectory(context), "${clip.id}.json")
        val captionsArr = JSONArray().apply {
            clip.captions.forEach { cap ->
                put(JSONObject().apply {
                    put("startSec", cap.startSec)
                    put("endSec", cap.endSec)
                    put("text", cap.text)
                })
            }
        }
        val json = JSONObject().apply {
            put("id", clip.id)
            put("filePath", clip.filePath)
            put("title", clip.title)
            put("hookText", clip.hookText)
            put("durationSec", clip.durationSec)
            put("captionStyle", clip.captionStyle.name)
            put("verticalBlur", clip.verticalBlur)
            put("viralScore", clip.viralScore)
            put("createdAtMs", clip.createdAtMs)
            put("captions", captionsArr)
        }
        metaFile.writeText(json.toString())
    }

    suspend fun loadSavedClips(context: Context): List<SavedClip> = withContext(Dispatchers.IO) {
        val dir = clipsDirectory(context)
        val metaFiles = dir.listFiles { file -> file.extension == "json" } ?: return@withContext emptyList()
        metaFiles.mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                val mp4Path = json.getString("filePath")
                val mp4File = File(mp4Path)
                if (!mp4File.exists() || mp4File.length() == 0L) return@mapNotNull null
                val captionsArr = json.optJSONArray("captions") ?: JSONArray()
                val captions = (0 until captionsArr.length()).mapNotNull { idx ->
                    val o = captionsArr.optJSONObject(idx) ?: return@mapNotNull null
                    CaptionSegment(
                        startSec = o.optDouble("startSec", 0.0),
                        endSec = o.optDouble("endSec", 1.5),
                        text = o.optString("text", "")
                    )
                }
                SavedClip(
                    id = json.getString("id"),
                    filePath = mp4Path,
                    title = json.optString("title", "Viral Short"),
                    hookText = json.optString("hookText", ""),
                    durationSec = json.optInt("durationSec", 30),
                    captionStyle = CaptionStyle.fromName(json.optString("captionStyle", "VIRAL_YELLOW")),
                    verticalBlur = json.optBoolean("verticalBlur", true),
                    viralScore = json.optInt("viralScore", 85),
                    createdAtMs = json.optLong("createdAtMs", mp4File.lastModified()),
                    captions = captions
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAtMs }
    }

    suspend fun deleteClip(context: Context, clip: SavedClip) = withContext(Dispatchers.IO) {
        File(clip.filePath).delete()
        File(clipsDirectory(context), "${clip.id}.json").delete()
    }

    suspend fun saveClipToGallery(context: Context, clip: SavedClip): Uri = withContext(Dispatchers.IO) {
        val sourceFile = File(clip.filePath)
        require(sourceFile.exists()) { "Clip file does not exist." }

        val resolver = context.contentResolver
        val fileName = "ViralClip_${clip.id}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ViralClipper")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val targetUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create MediaStore video entry.")

        resolver.openOutputStream(targetUri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Could not write video to gallery.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(targetUri, values, null, null)
        }
        targetUri
    }

    fun buildShareIntent(context: Context, clip: SavedClip): Intent {
        val file = File(clip.filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, clip.hookText.ifBlank { clip.title })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

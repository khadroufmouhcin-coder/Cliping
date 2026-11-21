package com.example.data

import android.util.Log
import com.example.model.LiveChatMessage
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "YouTubeService"

data class LiveChatPollResult(
    val messages: List<LiveChatMessage>,
    val nextContinuation: String?,
    val timeoutMs: Long
)

class YouTubeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun extractVideoId(url: String): String {
        val patterns = listOf(
            Regex("""youtu\.be/([0-9A-Za-z_-]{11})"""),
            Regex("""youtube\.com/live/([0-9A-Za-z_-]{11})"""),
            Regex("""youtube\.com/shorts/([0-9A-Za-z_-]{11})"""),
            Regex("""[?&]v=([0-9A-Za-z_-]{11})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(url.trim())
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    suspend fun fetchVideoInfo(rawUrl: String): YouTubeVideoInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(rawUrl)
        require(videoId.isNotEmpty()) { "Invalid YouTube URL. Paste a valid watch, youtu.be, shorts, or live link." }

        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        val encodedUrl = URLEncoder.encode(canonicalUrl, "UTF-8")

        // 1. Fetch reliable oEmbed metadata
        var title = "YouTube Video ($videoId)"
        var authorName = "YouTube Creator"
        var thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        runCatching {
            val oembedReq = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encodedUrl&format=json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(oembedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        title = json.optString("title", title)
                        authorName = json.optString("author_name", authorName)
                        thumbnailUrl = json.optString("thumbnail_url", thumbnailUrl)
                    }
                }
            }
        }

        // 2. Fetch watch page HTML for live stream status, Innertube key, HLS manifest, and live chat continuation
        var isLive = rawUrl.contains("/live/")
        var hlsManifestUrl: String? = null
        var continuation: String? = null
        var innertubeApiKey: String? = null

        val watchReq = Request.Builder()
            .url(canonicalUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(watchReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not load YouTube page (HTTP ${resp.code}).")
            }
            val html = resp.body?.string().orEmpty()

            innertubeApiKey = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
                .find(html)?.groupValues?.get(1)

            if (html.contains("\"isLive\":true") || html.contains("\"isLiveContent\":true")) {
                isLive = true
            }

            hlsManifestUrl = Regex(""""hlsManifestUrl"\s*:\s*"([^"]+)"""")
                .find(html)?.groupValues?.get(1)
                ?.replace("\\u0026", "&")
                ?.replace("\\/", "/")

            continuation = extractLiveChatContinuation(html)
        }

        YouTubeVideoInfo(
            videoId = videoId,
            originalUrl = canonicalUrl,
            title = title,
            authorName = authorName,
            thumbnailUrl = thumbnailUrl,
            isLive = isLive,
            hlsManifestUrl = hlsManifestUrl,
            liveChatContinuation = continuation,
            innertubeApiKey = innertubeApiKey
        )
    }

    private fun extractLiveChatContinuation(html: String): String? {
        val patterns = listOf(
            Regex(""""liveChatRenderer".*?"continuation"\s*:\s*"([^"]+)""""),
            Regex(""""reloadContinuationData"\s*:\s*\{\s*"continuation"\s*:\s*"([^"]+)""""),
            Regex(""""invalidationContinuationData"\s*:\s*\{\s*"continuation"\s*:\s*"([^"]+)"""")
        )
        for (p in patterns) {
            val match = p.find(html)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    suspend fun pollLiveChat(
        apiKey: String,
        continuation: String
    ): LiveChatPollResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20250101.00.00")
                })
            })
            put("continuation", continuation)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$apiKey")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Live chat API returned HTTP ${resp.code}.")
            }
            val body = resp.body?.string().orEmpty()
            val root = JSONObject(body)
            val liveChatContinuation = root
                .optJSONObject("continuationContents")
                ?.optJSONObject("liveChatContinuation")
                ?: return@withContext LiveChatPollResult(emptyList(), null, 3000L)

            val parsedMessages = mutableListOf<LiveChatMessage>()
            val actions = liveChatContinuation.optJSONArray("actions") ?: JSONArray()
            val nowMs = System.currentTimeMillis()

            for (i in 0 until actions.length()) {
                val action = actions.optJSONObject(i) ?: continue
                val renderer = action
                    .optJSONObject("addChatItemAction")
                    ?.optJSONObject("item")
                    ?.optJSONObject("liveChatTextMessageRenderer")
                    ?: continue

                val id = renderer.optString("id", "${nowMs}_$i")
                val author = renderer
                    .optJSONObject("authorName")
                    ?.optString("simpleText")
                    ?: "Viewer"

                val runs = renderer.optJSONObject("message")?.optJSONArray("runs")
                val text = buildString {
                    if (runs != null) {
                        for (r in 0 until runs.length()) {
                            val run = runs.optJSONObject(r) ?: continue
                            val t = run.optString("text", "")
                            if (t.isNotEmpty()) {
                                append(t)
                            } else {
                                val emojiShortcut = run.optJSONObject("emoji")
                                    ?.optJSONArray("shortcuts")
                                    ?.optString(0)
                                if (!emojiShortcut.isNullOrEmpty()) append(emojiShortcut)
                            }
                        }
                    }
                }.trim()

                if (text.isNotEmpty()) {
                    parsedMessages.add(
                        LiveChatMessage(
                            id = id,
                            author = author,
                            text = text,
                            timestampMs = nowMs
                        )
                    )
                }
            }

            var nextCont: String? = null
            var timeoutMs = 2500L
            val continuations = liveChatContinuation.optJSONArray("continuations")
            if (continuations != null && continuations.length() > 0) {
                val c0 = continuations.optJSONObject(0)
                val inv = c0?.optJSONObject("invalidationContinuationData")
                val timed = c0?.optJSONObject("timedContinuationData")
                val reload = c0?.optJSONObject("reloadContinuationData")
                val chosen = inv ?: timed ?: reload
                if (chosen != null) {
                    nextCont = chosen.optString("continuation").takeIf { it.isNotBlank() }
                    timeoutMs = chosen.optLong("timeoutMs", 2500L).coerceIn(1500L, 5000L)
                }
            }

            Log.d(TAG, "Polled ${parsedMessages.size} live chat messages")
            LiveChatPollResult(
                messages = parsedMessages,
                nextContinuation = nextCont,
                timeoutMs = timeoutMs
            )
        }
    }
}

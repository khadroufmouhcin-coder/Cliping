package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CaptionSegment
import com.example.model.LiveChatMessage
import com.example.model.ViralAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiViralRepository"

data class LiveBurstAnalysis(
    val hookTitle: String,
    val viralScore: Int,
    val summary: String
)

class AiViralRepository {

    private val clipAnalysisSchema = Schema.obj(
        properties = mapOf(
            "suggestedHook" to Schema.string(
                description = "Short punchy uppercase viral hook title of 2 to 5 words"
            ),
            "viralScore" to Schema.integer(
                description = "Viral potential score from 1 to 100"
            ),
            "peakReason" to Schema.string(
                description = "One concise sentence explaining why this moment engages viewers"
            ),
            "captions" to Schema.array(
                description = "Timed word-chunk captions in 2 to 4 word phrases matching the clip timeline",
                items = Schema.obj(
                    properties = mapOf(
                        "startSec" to Schema.double(description = "Start time in seconds relative to clip start (0.0)"),
                        "endSec" to Schema.double(description = "End time in seconds relative to clip start"),
                        "text" to Schema.string(description = "2 to 4 word uppercase caption phrase")
                    )
                )
            )
        )
    )

    private val liveBurstSchema = Schema.obj(
        properties = mapOf(
            "hookTitle" to Schema.string(description = "Punchy 2 to 5 word uppercase hook title for the viral chat spike"),
            "viralScore" to Schema.integer(description = "Viral excitement score from 1 to 100"),
            "summary" to Schema.string(description = "Concise 1-2 sentence summary of what the live chat is reacting to")
        )
    )

    private val clipAnalysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = clipAnalysisSchema
            }
        )
    }

    private val liveBurstModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = liveBurstSchema
            }
        )
    }

    private val hookSuggestModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun analyzeClipAndGenerateCaptions(
        audioBytes: ByteArray?,
        keyframes: List<Bitmap>,
        videoTitle: String,
        durationSec: Int,
        generateCaptions: Boolean
    ): ViralAnalysisResult {
        return try {
            val response = withTimeout(60_000L) {
                clipAnalysisModel.generateContent(
                    content {
                        if (audioBytes != null && audioBytes.isNotEmpty()) {
                            inlineData(audioBytes, "audio/mp4")
                        }
                        keyframes.forEach { frame ->
                            image(frame)
                        }
                        text(
                            buildString {
                                append("You are an expert short-form viral video editor (TikTok/Reels/Shorts). ")
                                append("Analyze the attached ")
                                if (audioBytes != null) append("audio track ") else append("video keyframes ")
                                append("for a $durationSec-second clip titled \"$videoTitle\". ")
                                append("1) Create a bold, scroll-stopping uppercase hook headline (2-5 words, e.g. 'WAIT FOR THIS MOMENT!'). ")
                                append("2) Rate its viral energy from 1 to 100 and briefly explain why. ")
                                if (generateCaptions) {
                                    if (audioBytes != null) {
                                        append("3) Transcribe the spoken audio accurately into dynamic short-form caption chunks of 2 to 4 words each with precise startSec and endSec timestamps between 0.0 and $durationSec.0. If speech is sparse or music-only, describe key audio/visual beats cleanly.")
                                    } else {
                                        append("3) Generate 4 to 6 punchy visual caption beats timed evenly across 0.0 to $durationSec.0 seconds describing the on-screen action.")
                                    }
                                } else {
                                    append("3) Return an empty captions array [].")
                                }
                            }
                        )
                    }
                )
            }

            val jsonString = response.text
                ?: throw IllegalStateException("Gemini returned an empty analysis response.")
            val json = JSONObject(jsonString)

            val suggestedHook = json.optString("suggestedHook", "WAIT FOR IT!").trim().uppercase()
            val viralScore = json.optInt("viralScore", 82).coerceIn(1, 100)
            val peakReason = json.optString("peakReason", "High-energy moment ideal for 9:16 retention.")
            val captionsJson = json.optJSONArray("captions")
            val captions = buildList {
                if (generateCaptions && captionsJson != null) {
                    for (i in 0 until captionsJson.length()) {
                        val obj = captionsJson.optJSONObject(i) ?: continue
                        val start = obj.optDouble("startSec", 0.0).coerceIn(0.0, durationSec.toDouble())
                        val end = obj.optDouble("endSec", start + 1.5)
                            .coerceIn(start + 0.25, durationSec.toDouble())
                        val text = obj.optString("text", "").trim().uppercase()
                        if (text.isNotEmpty()) {
                            add(CaptionSegment(startSec = start, endSec = end, text = text))
                        }
                    }
                }
            }

            ViralAnalysisResult(
                captions = captions,
                suggestedHook = suggestedHook.ifEmpty { "WAIT FOR IT!" },
                viralScore = viralScore,
                peakReason = peakReason
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Clip analysis timed out", e)
            throw IllegalStateException("AI analysis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Clip analysis failed", e)
            throw e
        }
    }

    suspend fun suggestViralHook(videoTitle: String, extraContext: String = ""): String {
        return try {
            val response = withTimeout(30_000L) {
                hookSuggestModel.generateContent(
                    "Generate ONE ultra-punchy uppercase viral short-form video hook header (2 to 5 words max, no quotes, no hashtags) for a video titled \"$videoTitle\". ${if (extraContext.isNotBlank()) "Context: $extraContext" else ""}"
                )
            }
            response.text?.trim()?.replace("\"", "")?.uppercase()?.take(48)
                ?.ifBlank { "WAIT FOR IT!" } ?: "WAIT FOR IT!"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Hook suggestion failed", e)
            throw e
        }
    }

    suspend fun analyzeLiveChatBurst(
        streamTitle: String,
        messages: List<LiveChatMessage>,
        msgCount30s: Int
    ): LiveBurstAnalysis {
        return try {
            val sampleText = messages.takeLast(25).joinToString("\n") { "${it.author}: ${it.text}" }
            val response = withTimeout(45_000L) {
                liveBurstModel.generateContent(
                    "A YouTube live stream titled \"$streamTitle\" just hit $msgCount30s chat messages in 30 seconds. " +
                        "Analyze these recent chat messages and generate a viral short-form hook headline, viral score (1-100), and summary:\n$sampleText"
                )
            }
            val jsonString = response.text
                ?: throw IllegalStateException("Gemini returned an empty burst analysis.")
            val json = JSONObject(jsonString)
            LiveBurstAnalysis(
                hookTitle = json.optString("hookTitle", "INSANE MOMENT!").trim().uppercase(),
                viralScore = json.optInt("viralScore", 90).coerceIn(1, 100),
                summary = json.optString("summary", "Chat exploded during a peak live moment.")
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Live burst analysis timed out", e)
            throw IllegalStateException("AI burst analysis timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Live burst analysis failed", e)
            throw e
        }
    }
}

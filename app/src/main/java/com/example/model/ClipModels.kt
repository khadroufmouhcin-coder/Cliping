package com.example.model

import android.graphics.Color as AndroidColor

enum class CaptionStyle(
    val displayName: String,
    val primaryColorInt: Int,
    val composeColorHex: Long
) {
    VIRAL_YELLOW(
        displayName = "Viral Yellow",
        primaryColorInt = AndroidColor.rgb(250, 204, 21),
        composeColorHex = 0xFFFACC15
    ),
    NEON_CYAN(
        displayName = "Neon Cyan",
        primaryColorInt = AndroidColor.rgb(34, 211, 238),
        composeColorHex = 0xFF22D3EE
    ),
    MODERN_CLEAN(
        displayName = "Modern Clean",
        primaryColorInt = AndroidColor.WHITE,
        composeColorHex = 0xFFF8FAFC
    );

    companion object {
        fun fromName(name: String): CaptionStyle =
            entries.firstOrNull { it.name == name } ?: VIRAL_YELLOW
    }
}

enum class ClipDurationOption(val seconds: Int, val label: String) {
    SEC_10(10, "10s"),
    SEC_20(20, "20s"),
    SEC_30(30, "30s")
}

data class CaptionSegment(
    val startSec: Double,
    val endSec: Double,
    val text: String
)

data class ViralAnalysisResult(
    val captions: List<CaptionSegment>,
    val suggestedHook: String,
    val viralScore: Int,
    val peakReason: String
)

data class AudioEnergyProfile(
    val durationSec: Double,
    val secondEnergies: List<Float>,
    val peakStartSec: Double
)

data class YouTubeVideoInfo(
    val videoId: String,
    val originalUrl: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String,
    val isLive: Boolean,
    val hlsManifestUrl: String?,
    val liveChatContinuation: String?,
    val innertubeApiKey: String?
)

data class LiveChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val timestampMs: Long
)

enum class HypeLevel(val label: String) {
    CALM("CALM"),
    BUILDING("BUILDING MOMENTUM"),
    HIGH_VELOCITY("VERY HIGH VELOCITY"),
    PEAK_VIRAL("PEAK VIRAL HYPE")
}

data class LiveMomentCapture(
    val id: String,
    val videoId: String,
    val streamTitle: String,
    val capturedAtMs: Long,
    val chatVelocityPer30s: Int,
    val viralScore: Int,
    val hookTitle: String,
    val summary: String,
    val sampleMessages: List<String>,
    val hlsManifestUrl: String?
)

data class SavedClip(
    val id: String,
    val filePath: String,
    val title: String,
    val hookText: String,
    val durationSec: Int,
    val captionStyle: CaptionStyle,
    val verticalBlur: Boolean,
    val viralScore: Int,
    val createdAtMs: Long,
    val captions: List<CaptionSegment>
)

sealed interface ClipPipelineState {
    data object Idle : ClipPipelineState
    data class AnalyzingAudio(val stepMessage: String) : ClipPipelineState
    data class GeneratingCaptions(val stepMessage: String) : ClipPipelineState
    data class RenderingClip(val progress: Float, val stepMessage: String) : ClipPipelineState
    data class Success(val clip: SavedClip, val message: String) : ClipPipelineState
    data class Error(val message: String) : ClipPipelineState
}

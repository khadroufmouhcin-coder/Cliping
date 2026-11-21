package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiViralRepository
import com.example.data.AudioPeakAnalyzer
import com.example.data.ClipStorageRepository
import com.example.data.VerticalVideoRenderer
import com.example.data.YouTubeService
import com.example.model.AudioEnergyProfile
import com.example.model.CaptionSegment
import com.example.model.CaptionStyle
import com.example.model.ClipDurationOption
import com.example.model.ClipPipelineState
import com.example.model.HypeLevel
import com.example.model.LiveChatMessage
import com.example.model.LiveMomentCapture
import com.example.model.SavedClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ClipperViewModel"

data class ClipStudioUiState(
    val selectedVideoUri: Uri? = null,
    val videoTitle: String = "",
    val videoDurationSec: Double = 0.0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoThumbnail: Bitmap? = null,
    val youtubeUrlInput: String = "",
    val isAnalyzingYoutubeUrl: Boolean = false,
    val youtubeVideoInfo: YouTubeVideoInfo? = null,
    val youtubeStatusMessage: String? = null,
    val energyProfile: AudioEnergyProfile? = null,
    val isAnalyzingEnergy: Boolean = false,
    val selectedStartSec: Double = 0.0,
    val durationOption: ClipDurationOption = ClipDurationOption.SEC_30,
    val verticalBlur: Boolean = true,
    val enableCaptions: Boolean = true,
    val captionStyle: CaptionStyle = CaptionStyle.VIRAL_YELLOW,
    val enableHook: Boolean = true,
    val hookText: String = "WAIT FOR IT!",
    val isSuggestingHook: Boolean = false,
    val generatedCaptions: List<CaptionSegment> = emptyList(),
    val lastViralScore: Int? = null,
    val lastPeakReason: String? = null,
    val pipelineState: ClipPipelineState = ClipPipelineState.Idle
)

data class LiveMonitorUiState(
    val liveUrlInput: String = "",
    val durationOption: ClipDurationOption = ClipDurationOption.SEC_30,
    val hypeThreshold: Int = 30,
    val maxWaitSeconds: Int = 180,
    val verticalBlur: Boolean = true,
    val enableCaptions: Boolean = true,
    val captionStyle: CaptionStyle = CaptionStyle.NEON_CYAN,
    val enableHook: Boolean = true,
    val hookText: String = "INSANE MOMENT!",
    val isMonitoring: Boolean = false,
    val isConnecting: Boolean = false,
    val isAnalyzingBurst: Boolean = false,
    val elapsedSeconds: Int = 0,
    val messagesInLast30s: Int = 0,
    val hypeLevel: HypeLevel = HypeLevel.CALM,
    val velocityHistory: List<Int> = emptyList(),
    val recentMessages: List<LiveChatMessage> = emptyList(),
    val streamInfo: YouTubeVideoInfo? = null,
    val statusMessage: String = "Paste a live YouTube stream URL to track real-time chat velocity.",
    val errorMessage: String? = null,
    val capturedMoments: List<LiveMomentCapture> = emptyList()
)

data class VaultUiState(
    val clips: List<SavedClip> = emptyList(),
    val activePlayerClip: SavedClip? = null,
    val feedbackMessage: String? = null
)

class ClipperViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AiViralRepository()
    private val youTubeService = YouTubeService()

    private val _studioState = MutableStateFlow(ClipStudioUiState())
    val studioState: StateFlow<ClipStudioUiState> = _studioState.asStateFlow()

    private val _liveState = MutableStateFlow(LiveMonitorUiState())
    val liveState: StateFlow<LiveMonitorUiState> = _liveState.asStateFlow()

    private val _vaultState = MutableStateFlow(VaultUiState())
    val vaultState: StateFlow<VaultUiState> = _vaultState.asStateFlow()

    private var liveMonitorJob: Job? = null
    private var clipGenerationJob: Job? = null

    init {
        refreshVault()
    }

    // -------------------------------------------------------------------------
    // Video Clipper Studio Actions
    // -------------------------------------------------------------------------

    fun onVideoPicked(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    selectedVideoUri = uri,
                    isAnalyzingEnergy = true,
                    pipelineState = ClipPipelineState.Idle,
                    youtubeStatusMessage = null,
                    generatedCaptions = emptyList(),
                    lastViralScore = null,
                    lastPeakReason = null
                )
            }
            try {
                val meta = AudioPeakAnalyzer.getVideoMetadata(context, uri)
                _studioState.update {
                    it.copy(
                        videoTitle = meta.displayName.substringBeforeLast("."),
                        videoDurationSec = meta.durationSec,
                        videoWidth = meta.width,
                        videoHeight = meta.height,
                        videoThumbnail = meta.thumbnail
                    )
                }
                val clipSec = _studioState.value.durationOption.seconds
                val profile = AudioPeakAnalyzer.analyzeAudioEnergy(context, uri, clipSec)
                _studioState.update {
                    it.copy(
                        energyProfile = profile,
                        selectedStartSec = profile.peakStartSec,
                        isAnalyzingEnergy = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading picked video", e)
                _studioState.update {
                    it.copy(
                        isAnalyzingEnergy = false,
                        pipelineState = ClipPipelineState.Error(
                            e.localizedMessage ?: "Failed to read selected video."
                        )
                    )
                }
            }
        }
    }

    fun updateYoutubeUrlInput(url: String) {
        _studioState.update { it.copy(youtubeUrlInput = url) }
    }

    fun analyzeYouTubeUrl() {
        val url = _studioState.value.youtubeUrlInput.trim()
        if (url.isEmpty()) {
            _studioState.update {
                it.copy(youtubeStatusMessage = "Enter a YouTube URL to inspect.")
            }
            return
        }
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    isAnalyzingYoutubeUrl = true,
                    youtubeStatusMessage = "Fetching YouTube metadata..."
                )
            }
            try {
                val info = youTubeService.fetchVideoInfo(url)
                val suggestedHook = runCatching {
                    aiRepository.suggestViralHook(info.title, "Channel: ${info.authorName}")
                }.getOrNull()

                val status = if (info.hlsManifestUrl != null) {
                    "Stream detected! Ready to clip or monitor live hype."
                } else {
                    "Loaded \"${info.title}\". Pick a downloaded video file from your device below to render an offline 9:16 MP4 with AI captions."
                }
                _studioState.update {
                    it.copy(
                        isAnalyzingYoutubeUrl = false,
                        youtubeVideoInfo = info,
                        videoTitle = if (it.selectedVideoUri == null) info.title else it.videoTitle,
                        hookText = suggestedHook ?: it.hookText,
                        youtubeStatusMessage = status
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "YouTube URL lookup failed", e)
                _studioState.update {
                    it.copy(
                        isAnalyzingYoutubeUrl = false,
                        youtubeStatusMessage = e.localizedMessage ?: "Could not inspect YouTube link."
                    )
                }
            }
        }
    }

    fun selectDuration(option: ClipDurationOption) {
        val state = _studioState.value
        val profile = state.energyProfile
        if (profile == null) {
            _studioState.update { it.copy(durationOption = option) }
            return
        }
        val energies = profile.secondEnergies
        val dur = option.seconds
        var bestStart = 0
        var bestMean = -1f
        val limit = max(1, energies.size - dur)
        for (s in 0 until limit) {
            val end = min(energies.size, s + dur)
            var sum = 0f
            for (k in s until end) sum += energies[k]
            val mean = sum / max(1, end - s)
            if (mean > bestMean) {
                bestMean = mean
                bestStart = s
            }
        }
        val peakStart = max(0.0, (bestStart - 5).toDouble())
            .coerceAtMost(max(0.0, profile.durationSec - dur))

        _studioState.update {
            it.copy(
                durationOption = option,
                selectedStartSec = peakStart,
                energyProfile = profile.copy(peakStartSec = peakStart)
            )
        }
    }

    fun updateStartSec(startSec: Double) {
        val maxStart = max(0.0, _studioState.value.videoDurationSec - _studioState.value.durationOption.seconds)
        _studioState.update {
            it.copy(selectedStartSec = startSec.coerceIn(0.0, maxStart))
        }
    }

    fun toggleVerticalBlur(enabled: Boolean) {
        _studioState.update { it.copy(verticalBlur = enabled) }
    }

    fun toggleCaptions(enabled: Boolean) {
        _studioState.update { it.copy(enableCaptions = enabled) }
    }

    fun selectCaptionStyle(style: CaptionStyle) {
        _studioState.update { it.copy(captionStyle = style) }
    }

    fun toggleHook(enabled: Boolean) {
        _studioState.update { it.copy(enableHook = enabled) }
    }

    fun updateHookText(text: String) {
        _studioState.update { it.copy(hookText = text) }
    }

    fun suggestHookWithAi() {
        val state = _studioState.value
        val title = state.videoTitle.ifBlank { state.youtubeVideoInfo?.title ?: "Viral Clip" }
        viewModelScope.launch {
            _studioState.update { it.copy(isSuggestingHook = true) }
            try {
                val hook = aiRepository.suggestViralHook(
                    videoTitle = title,
                    extraContext = "Duration ${state.durationOption.seconds}s short clip"
                )
                _studioState.update { it.copy(hookText = hook, isSuggestingHook = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI hook suggestion failed", e)
                _studioState.update {
                    it.copy(
                        isSuggestingHook = false,
                        pipelineState = ClipPipelineState.Error(
                            e.localizedMessage ?: "AI hook suggestion failed."
                        )
                    )
                }
            }
        }
    }

    fun generateViralClip() {
        val state = _studioState.value
        val sourceUri = state.selectedVideoUri ?: state.youtubeVideoInfo?.hlsManifestUrl?.let { Uri.parse(it) }
        if (sourceUri == null) {
            _studioState.update {
                it.copy(
                    pipelineState = ClipPipelineState.Error(
                        "Pick a video from your device first to render a 9:16 MP4 short."
                    )
                )
            }
            return
        }

        clipGenerationJob?.cancel()
        clipGenerationJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val actualDuration = if (state.videoDurationSec > 0.5) {
                    min(state.durationOption.seconds.toDouble(), state.videoDurationSec).toInt()
                        .coerceAtLeast(1)
                } else {
                    state.durationOption.seconds
                }

                // 1. Extract clip audio / fallback keyframes
                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.AnalyzingAudio(
                            "Extracting peak window (${state.selectedStartSec.toInt()}s)..."
                        )
                    )
                }
                val audioBytes = if (state.selectedVideoUri != null) {
                    AudioPeakAnalyzer.extractAudioClipBytes(
                        context = context,
                        uri = sourceUri,
                        startSec = state.selectedStartSec,
                        durationSec = actualDuration
                    )
                } else null

                val keyframes = if (audioBytes == null && state.selectedVideoUri != null) {
                    AudioPeakAnalyzer.extractKeyFrames(
                        context = context,
                        uri = sourceUri,
                        startSec = state.selectedStartSec,
                        durationSec = actualDuration
                    )
                } else emptyList()

                // 2. Gemini AI viral captions + hook + score
                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.GeneratingCaptions(
                            if (state.enableCaptions) "Generating AI auto-captions & viral score..."
                            else "Analyzing viral moment with Gemini..."
                        )
                    )
                }

                val aiResult = aiRepository.analyzeClipAndGenerateCaptions(
                    audioBytes = audioBytes,
                    keyframes = keyframes,
                    videoTitle = state.videoTitle.ifBlank { "Viral Clip" },
                    durationSec = actualDuration,
                    generateCaptions = state.enableCaptions
                )
                keyframes.forEach { if (!it.isRecycled) it.recycle() }

                val finalHook = if (state.enableHook && state.hookText.isBlank()) {
                    aiResult.suggestedHook
                } else {
                    state.hookText
                }

                _studioState.update {
                    it.copy(
                        generatedCaptions = aiResult.captions,
                        lastViralScore = aiResult.viralScore,
                        lastPeakReason = aiResult.peakReason,
                        hookText = finalHook
                    )
                }

                // 3. Render 1080x1920 9:16 MP4 with Media3 Transformer
                val (clipId, outFile) = ClipStorageRepository.newClipOutputFile(context)
                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.RenderingClip(
                            progress = 0.05f,
                            stepMessage = "Rendering 1080x1920 9:16 short..."
                        )
                    )
                }

                VerticalVideoRenderer.renderVerticalClip(
                    context = context,
                    inputUri = sourceUri,
                    outputFile = outFile,
                    startSec = state.selectedStartSec,
                    durationSec = actualDuration,
                    verticalBlur = state.verticalBlur,
                    enableCaptions = state.enableCaptions,
                    captionStyle = state.captionStyle,
                    captions = aiResult.captions,
                    enableHook = state.enableHook,
                    hookText = finalHook,
                    onProgress = { progress ->
                        _studioState.update { current ->
                            if (current.pipelineState is ClipPipelineState.RenderingClip) {
                                current.copy(
                                    pipelineState = ClipPipelineState.RenderingClip(
                                        progress = progress,
                                        stepMessage = "Encoding 9:16 video (${(progress * 100).toInt()}%)..."
                                    )
                                )
                            } else current
                        }
                    }
                )

                val savedClip = SavedClip(
                    id = clipId,
                    filePath = outFile.absolutePath,
                    title = state.videoTitle.ifBlank { "Viral Short" },
                    hookText = if (state.enableHook) finalHook else "",
                    durationSec = actualDuration,
                    captionStyle = state.captionStyle,
                    verticalBlur = state.verticalBlur,
                    viralScore = aiResult.viralScore,
                    createdAtMs = System.currentTimeMillis(),
                    captions = aiResult.captions
                )
                ClipStorageRepository.saveClipMetadata(context, savedClip)
                refreshVault()

                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.Success(
                            clip = savedClip,
                            message = "Ready! Clipped from ${state.selectedStartSec.toInt()}s • Viral Score ${aiResult.viralScore}/100"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clip generation pipeline failed", e)
                _studioState.update {
                    it.copy(
                        pipelineState = ClipPipelineState.Error(
                            e.localizedMessage ?: "Failed to generate clip."
                        )
                    )
                }
            }
        }
    }

    fun resetPipelineState() {
        _studioState.update { it.copy(pipelineState = ClipPipelineState.Idle) }
    }

    // -------------------------------------------------------------------------
    // Live Stream Hype Monitor Actions
    // -------------------------------------------------------------------------

    fun updateLiveUrl(url: String) {
        _liveState.update { it.copy(liveUrlInput = url, errorMessage = null) }
    }

    fun updateLiveDuration(option: ClipDurationOption) {
        _liveState.update { it.copy(durationOption = option) }
    }

    fun updateLiveThreshold(threshold: Int) {
        _liveState.update { it.copy(hypeThreshold = threshold) }
    }

    fun updateLiveMaxWait(seconds: Int) {
        _liveState.update { it.copy(maxWaitSeconds = seconds) }
    }

    fun toggleLiveBlur(enabled: Boolean) {
        _liveState.update { it.copy(verticalBlur = enabled) }
    }

    fun toggleLiveCaptions(enabled: Boolean) {
        _liveState.update { it.copy(enableCaptions = enabled) }
    }

    fun selectLiveCaptionStyle(style: CaptionStyle) {
        _liveState.update { it.copy(captionStyle = style) }
    }

    fun toggleLiveHook(enabled: Boolean) {
        _liveState.update { it.copy(enableHook = enabled) }
    }

    fun updateLiveHookText(text: String) {
        _liveState.update { it.copy(hookText = text) }
    }

    fun startLiveMonitoring() {
        val current = _liveState.value
        val url = current.liveUrlInput.trim()
        if (url.isEmpty()) {
            _liveState.update { it.copy(errorMessage = "Enter a YouTube Live stream link first.") }
            return
        }

        liveMonitorJob?.cancel()
        liveMonitorJob = viewModelScope.launch {
            _liveState.update {
                it.copy(
                    isConnecting = true,
                    isMonitoring = false,
                    errorMessage = null,
                    elapsedSeconds = 0,
                    messagesInLast30s = 0,
                    hypeLevel = HypeLevel.CALM,
                    velocityHistory = emptyList(),
                    recentMessages = emptyList(),
                    statusMessage = "Connecting to YouTube live stream..."
                )
            }

            try {
                val info = youTubeService.fetchVideoInfo(url)
                val apiKey = info.innertubeApiKey
                var continuation = info.liveChatContinuation

                if (apiKey.isNullOrBlank() || continuation.isNullOrBlank()) {
                    _liveState.update {
                        it.copy(
                            isConnecting = false,
                            streamInfo = info,
                            errorMessage = "Live chat is unavailable on this link. Make sure the YouTube video is currently live with chat enabled."
                        )
                    }
                    return@launch
                }

                _liveState.update {
                    it.copy(
                        isConnecting = false,
                        isMonitoring = true,
                        streamInfo = info,
                        statusMessage = "Monitoring live chat on \"${info.title}\"..."
                    )
                }

                val timestamps = ArrayDeque<Long>()
                val seenIds = LinkedHashSet<String>()
                val startTimeMs = System.currentTimeMillis()
                var hyped = false

                while (isActive) {
                    val nowMs = System.currentTimeMillis()
                    val elapsed = ((nowMs - startTimeMs) / 1000L).toInt()
                    if (elapsed >= _liveState.value.maxWaitSeconds) break

                    val poll = youTubeService.pollLiveChat(apiKey, continuation ?: break)
                    continuation = poll.nextContinuation ?: continuation

                    val fresh = poll.messages.filter { msg -> seenIds.add(msg.id) }
                    if (seenIds.size > 1000) {
                        val iter = seenIds.iterator()
                        repeat(300) { if (iter.hasNext()) { iter.next(); iter.remove() } }
                    }

                    val pollNowMs = System.currentTimeMillis()
                    fresh.forEach { timestamps.addLast(pollNowMs) }
                    while (timestamps.isNotEmpty() && pollNowMs - timestamps.first() > 30_000L) {
                        timestamps.removeFirst()
                    }

                    val count = timestamps.size
                    val threshold = _liveState.value.hypeThreshold.coerceAtLeast(1)
                    val level = computeHypeLevel(count, threshold)

                    _liveState.update { s ->
                        s.copy(
                            elapsedSeconds = ((pollNowMs - startTimeMs) / 1000L).toInt(),
                            messagesInLast30s = count,
                            hypeLevel = level,
                            velocityHistory = (s.velocityHistory + count).takeLast(30),
                            recentMessages = (s.recentMessages + fresh).takeLast(40),
                            statusMessage = "Watching live chat (${((pollNowMs - startTimeMs) / 1000L)}s) • $count msgs/30s"
                        )
                    }

                    if (count >= threshold) {
                        hyped = true
                        _liveState.update {
                            it.copy(
                                isMonitoring = false,
                                isAnalyzingBurst = true,
                                statusMessage = "VIRAL MOMENT DETECTED! Analyzing burst with Gemini..."
                            )
                        }
                        captureBurstWithAi(
                            info = info,
                            messages = _liveState.value.recentMessages,
                            velocity = count
                        )
                        break
                    }

                    delay(poll.timeoutMs)
                }

                if (!hyped && isActive) {
                    _liveState.update {
                        it.copy(
                            isMonitoring = false,
                            statusMessage = "Monitoring window (${_liveState.value.maxWaitSeconds}s) ended without crossing threshold."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live chat monitoring error", e)
                _liveState.update {
                    it.copy(
                        isConnecting = false,
                        isMonitoring = false,
                        isAnalyzingBurst = false,
                        errorMessage = e.localizedMessage ?: "Live monitoring failed."
                    )
                }
            }
        }
    }

    fun stopLiveMonitoring() {
        liveMonitorJob?.cancel()
        liveMonitorJob = null
        _liveState.update {
            it.copy(
                isConnecting = false,
                isMonitoring = false,
                statusMessage = "Live monitoring stopped."
            )
        }
    }

    fun captureCurrentLiveMoment() {
        val state = _liveState.value
        val info = state.streamInfo ?: return
        if (state.recentMessages.isEmpty()) {
            _liveState.update { it.copy(errorMessage = "No chat messages captured yet to analyze.") }
            return
        }
        viewModelScope.launch {
            _liveState.update { it.copy(isAnalyzingBurst = true, errorMessage = null) }
            try {
                captureBurstWithAi(info, state.recentMessages, state.messagesInLast30s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual live burst capture failed", e)
                _liveState.update {
                    it.copy(
                        isAnalyzingBurst = false,
                        errorMessage = e.localizedMessage ?: "Failed to analyze live chat burst."
                    )
                }
            }
        }
    }

    private suspend fun captureBurstWithAi(
        info: YouTubeVideoInfo,
        messages: List<LiveChatMessage>,
        velocity: Int
    ) {
        val burst = aiRepository.analyzeLiveChatBurst(
            streamTitle = info.title,
            messages = messages,
            msgCount30s = velocity
        )
        val hook = if (_liveState.value.enableHook && _liveState.value.hookText.isNotBlank() &&
            _liveState.value.hookText != "INSANE MOMENT!"
        ) {
            _liveState.value.hookText
        } else {
            burst.hookTitle
        }

        val capture = LiveMomentCapture(
            id = UUID.randomUUID().toString().take(8),
            videoId = info.videoId,
            streamTitle = info.title,
            capturedAtMs = System.currentTimeMillis(),
            chatVelocityPer30s = velocity,
            viralScore = burst.viralScore,
            hookTitle = hook,
            summary = burst.summary,
            sampleMessages = messages.takeLast(5).map { "${it.author}: ${it.text}" },
            hlsManifestUrl = info.hlsManifestUrl
        )
        _liveState.update {
            it.copy(
                isAnalyzingBurst = false,
                hookText = hook,
                capturedMoments = listOf(capture) + it.capturedMoments,
                statusMessage = "Captured viral moment ($velocity msgs/30s) • Score ${burst.viralScore}/100"
            )
        }
    }

    private fun computeHypeLevel(count: Int, threshold: Int): HypeLevel {
        val ratio = count.toFloat() / max(1, threshold).toFloat()
        return when {
            ratio >= 1.0f -> HypeLevel.PEAK_VIRAL
            ratio >= 0.7f -> HypeLevel.HIGH_VELOCITY
            ratio >= 0.35f -> HypeLevel.BUILDING
            else -> HypeLevel.CALM
        }
    }

    // -------------------------------------------------------------------------
    // Clips Vault Actions
    // -------------------------------------------------------------------------

    fun refreshVault() {
        viewModelScope.launch {
            val clips = ClipStorageRepository.loadSavedClips(getApplication())
            _vaultState.update { it.copy(clips = clips) }
        }
    }

    fun openClipPlayer(clip: SavedClip) {
        _vaultState.update { it.copy(activePlayerClip = clip) }
    }

    fun closeClipPlayer() {
        _vaultState.update { it.copy(activePlayerClip = null) }
    }

    fun saveClipToGallery(clip: SavedClip) {
        viewModelScope.launch {
            try {
                ClipStorageRepository.saveClipToGallery(getApplication(), clip)
                _vaultState.update {
                    it.copy(feedbackMessage = "Saved \"${clip.hookText.ifBlank { clip.title }}\" to Movies/ViralClipper.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gallery save failed", e)
                _vaultState.update {
                    it.copy(feedbackMessage = e.localizedMessage ?: "Failed to save to gallery.")
                }
            }
        }
    }

    fun deleteClip(clip: SavedClip) {
        viewModelScope.launch {
            ClipStorageRepository.deleteClip(getApplication(), clip)
            refreshVault()
            _vaultState.update {
                it.copy(
                    activePlayerClip = if (it.activePlayerClip?.id == clip.id) null else it.activePlayerClip,
                    feedbackMessage = "Clip deleted."
                )
            }
        }
    }

    fun clearVaultFeedback() {
        _vaultState.update { it.copy(feedbackMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        liveMonitorJob?.cancel()
        clipGenerationJob?.cancel()
    }
}

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CaptionStyle
import com.example.model.ClipDurationOption
import com.example.model.ClipPipelineState
import com.example.model.SavedClip
import com.example.ui.components.AudioEnergyWaveformCard
import com.example.ui.components.CaptionStyleSelector
import com.example.ui.components.DurationSegmentedRow
import com.example.ui.components.Short916PreviewCard
import com.example.ui.components.StudioHeroBanner
import com.example.ui.components.formatTime
import com.example.ui.theme.DeckContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.ViralYellow
import com.example.viewmodel.ClipStudioUiState
import kotlin.math.max

@Composable
fun ClipStudioScreen(
    state: ClipStudioUiState,
    onVideoPicked: (android.net.Uri) -> Unit,
    onYoutubeUrlChanged: (String) -> Unit,
    onAnalyzeYoutubeUrl: () -> Unit,
    onSelectDuration: (ClipDurationOption) -> Unit,
    onStartSecChanged: (Double) -> Unit,
    onSnapToPeak: () -> Unit,
    onToggleVerticalBlur: (Boolean) -> Unit,
    onToggleCaptions: (Boolean) -> Unit,
    onSelectCaptionStyle: (CaptionStyle) -> Unit,
    onToggleHook: (Boolean) -> Unit,
    onHookTextChanged: (String) -> Unit,
    onSuggestHookWithAi: () -> Unit,
    onGenerateClip: () -> Unit,
    onPlayGeneratedClip: (SavedClip) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onVideoPicked(uri)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StudioHeroBanner()
            }

            // Source Selection Card (Photo Picker + YouTube URL Inspector)
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MovieCreation,
                                contentDescription = null,
                                tint = ElectricViolet
                            )
                            Text(
                                text = "1. Video Source",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pick_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedVideoUri == null) {
                                    "Pick Video from Device"
                                } else {
                                    "Change Device Video"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (state.selectedVideoUri != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = DeckContainer,
                                border = BorderStroke(1.dp, MintSuccess.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = state.videoTitle.ifBlank { "Selected Device Video" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatTime(state.videoDurationSec)} • ${state.videoWidth}x${state.videoHeight}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NeonCyan
                                        )
                                    }
                                    if (state.isAnalyzingEnergy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = ViralYellow,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Video ready",
                                            tint = MintSuccess
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.youtubeUrlInput,
                            onValueChange = onYoutubeUrlChanged,
                            label = { Text("YouTube Video URL (Optional AI Hook & Info)") },
                            placeholder = { Text(stringResource(R.string.youtube_url_hint)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    tint = MutedSlate
                                )
                            },
                            trailingIcon = {
                                if (state.isAnalyzingYoutubeUrl) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricViolet
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricViolet,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("youtube_url_input")
                        )

                        OutlinedButton(
                            onClick = onAnalyzeYoutubeUrl,
                            enabled = !state.isAnalyzingYoutubeUrl,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("analyze_youtube_url_button")
                        ) {
                            Text("Inspect YouTube URL & Suggest Hook")
                        }

                        state.youtubeVideoInfo?.let { info ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = DeckContainer,
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = info.thumbnailUrl,
                                        contentDescription = info.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 96.dp, height = 54.dp)
                                            .clip(MaterialTheme.shapes.extraSmall)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = info.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = info.authorName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MutedSlate
                                        )
                                    }
                                }
                            }
                        }

                        state.youtubeStatusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonCyan
                            )
                        }
                    }
                }
            }

            // Audio Peak Waveform & Trim Window
            if (state.energyProfile != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AudioEnergyWaveformCard(
                            profile = state.energyProfile,
                            selectedStartSec = state.selectedStartSec,
                            clipDurationSec = state.durationOption.seconds,
                            onStartChanged = onStartSecChanged,
                            onSnapToPeak = onSnapToPeak
                        )

                        val maxStart = max(
                            1f,
                            (state.videoDurationSec - state.durationOption.seconds).toFloat()
                        )
                        if (state.videoDurationSec > state.durationOption.seconds) {
                            Slider(
                                value = state.selectedStartSec.toFloat().coerceIn(0f, maxStart),
                                onValueChange = { onStartSecChanged(it.toDouble()) },
                                valueRange = 0f..maxStart,
                                colors = SliderDefaults.colors(
                                    thumbColor = ViralYellow,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("clip_start_slider")
                            )
                        }
                    }
                }
            }

            // Clip Studio Settings + Live 9:16 Preview
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "2. Viral Short Formatting",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Clip Duration",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedSlate
                            )
                            DurationSegmentedRow(
                                selected = state.durationOption,
                                onSelect = onSelectDuration
                            )
                        }

                        StudioToggleRow(
                            title = "9:16 Vertical Blur Background",
                            subtitle = "Keeps full horizontal frame with blurred vertical backdrop",
                            checked = state.verticalBlur,
                            onCheckedChange = onToggleVerticalBlur,
                            testTag = "vertical_blur_switch"
                        )

                        StudioToggleRow(
                            title = "Dynamic AI Auto-Captions",
                            subtitle = "Gemini 3.8 Flash transcribes & syncs bold viral captions",
                            checked = state.enableCaptions,
                            onCheckedChange = onToggleCaptions,
                            testTag = "auto_captions_switch"
                        )

                        AnimatedVisibility(visible = state.enableCaptions) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Caption Style",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MutedSlate
                                )
                                CaptionStyleSelector(
                                    selected = state.captionStyle,
                                    onSelect = onSelectCaptionStyle
                                )
                            }
                        }

                        StudioToggleRow(
                            title = "Hook Title Header",
                            subtitle = "Top banner headline to stop the scroll in the first 2 seconds",
                            checked = state.enableHook,
                            onCheckedChange = onToggleHook,
                            testTag = "hook_header_switch"
                        )

                        AnimatedVisibility(visible = state.enableHook) {
                            OutlinedTextField(
                                value = state.hookText,
                                onValueChange = onHookTextChanged,
                                label = { Text("Top Hook Text") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = onSuggestHookWithAi,
                                        enabled = !state.isSuggestingHook,
                                        modifier = Modifier.testTag("ai_suggest_hook_button")
                                    ) {
                                        if (state.isSuggestingHook) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = ViralYellow
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = "Suggest viral hook with AI",
                                                tint = ViralYellow
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricViolet,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hook_text_input")
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Short916PreviewCard(
                                thumbnail = state.videoThumbnail,
                                hookEnabled = state.enableHook,
                                hookText = state.hookText,
                                captionsEnabled = state.enableCaptions,
                                captionStyle = state.captionStyle,
                                verticalBlur = state.verticalBlur,
                                previewCaption = state.generatedCaptions.firstOrNull()?.text
                            )
                        }
                    }
                }
            }

            // Generate CTA & Pipeline Status
            item {
                val isProcessing = state.pipelineState is ClipPipelineState.AnalyzingAudio ||
                    state.pipelineState is ClipPipelineState.GeneratingCaptions ||
                    state.pipelineState is ClipPipelineState.RenderingClip

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onGenerateClip,
                        enabled = !isProcessing && (state.selectedVideoUri != null || state.youtubeVideoInfo?.hlsManifestUrl != null),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ViralYellow,
                            contentColor = ObsidianBg,
                            disabledContainerColor = DeckContainer,
                            disabledContentColor = MutedSlate
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("generate_viral_clip_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ObsidianBg,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Processing Viral Short...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate 9:16 Viral Clip",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    PipelineStatusCard(
                        pipelineState = state.pipelineState,
                        peakReason = state.lastPeakReason,
                        onPlayClip = onPlayGeneratedClip
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StudioToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricViolet
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun PipelineStatusCard(
    pipelineState: ClipPipelineState,
    peakReason: String?,
    onPlayClip: (SavedClip) -> Unit
) {
    when (pipelineState) {
        ClipPipelineState.Idle -> Unit
        is ClipPipelineState.AnalyzingAudio -> {
            StatusBannerSurface(
                borderColor = NeonCyan,
                title = pipelineState.stepMessage,
                progress = null
            )
        }
        is ClipPipelineState.GeneratingCaptions -> {
            StatusBannerSurface(
                borderColor = ElectricViolet,
                title = pipelineState.stepMessage,
                progress = null
            )
        }
        is ClipPipelineState.RenderingClip -> {
            StatusBannerSurface(
                borderColor = ViralYellow,
                title = pipelineState.stepMessage,
                progress = pipelineState.progress
            )
        }
        is ClipPipelineState.Success -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurface,
                border = BorderStroke(1.5.dp, MintSuccess)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MintSuccess
                        )
                        Text(
                            text = pipelineState.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MintSuccess
                        )
                    }
                    if (!peakReason.isNullOrBlank()) {
                        Text(
                            text = peakReason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )
                    }
                    Button(
                        onClick = { onPlayClip(pipelineState.clip) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("play_generated_clip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Ready 9:16 Clip")
                    }
                }
            }
        }
        is ClipPipelineState.Error -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurface,
                border = BorderStroke(1.5.dp, PeakRed)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = PeakRed
                    )
                    Text(
                        text = pipelineState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PeakRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBannerSurface(
    borderColor: Color,
    title: String,
    progress: Float?
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = borderColor,
                    trackColor = ObsidianBg
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = borderColor,
                    trackColor = ObsidianBg
                )
            }
        }
    }
}

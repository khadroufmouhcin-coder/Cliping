package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CaptionStyle
import com.example.model.ClipDurationOption
import com.example.model.LiveMomentCapture
import com.example.ui.components.CaptionStyleSelector
import com.example.ui.components.DurationSegmentedRow
import com.example.ui.components.HypeMeterCard
import com.example.ui.theme.DeckContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HypeOrange
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakRed
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.ViralYellow
import com.example.viewmodel.LiveMonitorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveMonitorScreen(
    state: LiveMonitorUiState,
    onUrlChanged: (String) -> Unit,
    onDurationChanged: (ClipDurationOption) -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onMaxWaitChanged: (Int) -> Unit,
    onCaptionStyleChanged: (CaptionStyle) -> Unit,
    onHookTextChanged: (String) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onCaptureMoment: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Podcasts,
                                contentDescription = null,
                                tint = PeakRed
                            )
                            Text(
                                text = "Live Stream Hype Radar",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                        }

                        OutlinedTextField(
                            value = state.liveUrlInput,
                            onValueChange = onUrlChanged,
                            label = { Text("YouTube Live Stream Link") },
                            placeholder = { Text(stringResource(R.string.live_url_hint)) },
                            singleLine = true,
                            enabled = !state.isMonitoring && !state.isConnecting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricViolet,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_url_input")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Target Clip Duration",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedSlate
                            )
                            DurationSegmentedRow(
                                selected = state.durationOption,
                                onSelect = onDurationChanged
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Hype Threshold (msgs / 30s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlate
                                )
                                Text(
                                    text = "${state.hypeThreshold} msgs",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ViralYellow
                                )
                            }
                            Slider(
                                value = state.hypeThreshold.toFloat(),
                                onValueChange = { onThresholdChanged((it / 5f).toInt() * 5) },
                                valueRange = 5f..200f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ViralYellow,
                                    activeTrackColor = HypeOrange
                                ),
                                modifier = Modifier.testTag("hype_threshold_slider")
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Monitoring Window",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlate
                                )
                                Text(
                                    text = "${state.maxWaitSeconds}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan
                                )
                            }
                            Slider(
                                value = state.maxWaitSeconds.toFloat(),
                                onValueChange = { onMaxWaitChanged((it / 30f).toInt() * 30) },
                                valueRange = 30f..900f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = ElectricViolet
                                ),
                                modifier = Modifier.testTag("max_wait_slider")
                            )
                        }

                        CaptionStyleSelector(
                            selected = state.captionStyle,
                            onSelect = onCaptionStyleChanged
                        )

                        OutlinedTextField(
                            value = state.hookText,
                            onValueChange = onHookTextChanged,
                            label = { Text("Top Hook Text") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricViolet,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_hook_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.isMonitoring || state.isConnecting) {
                                Button(
                                    onClick = onStopMonitoring,
                                    colors = ButtonDefaults.buttonColors(containerColor = PeakRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("stop_live_monitor_button")
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop Radar")
                                }
                            } else {
                                Button(
                                    onClick = onStartMonitoring,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("start_live_monitor_button")
                                ) {
                                    Icon(Icons.Filled.RadioButtonChecked, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Monitoring Live Chat")
                                }
                            }
                        }
                    }
                }
            }

            item {
                HypeMeterCard(
                    countIn30s = state.messagesInLast30s,
                    threshold = state.hypeThreshold,
                    hypeLevel = state.hypeLevel,
                    velocityHistory = state.velocityHistory
                )
            }

            if (state.isMonitoring && state.recentMessages.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = onCaptureMoment,
                        enabled = !state.isAnalyzingBurst,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("capture_burst_now_button")
                    ) {
                        if (state.isAnalyzingBurst) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ViralYellow
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = ViralYellow
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Current Chat Burst with Gemini")
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = DeckContainer,
                    border = BorderStroke(
                        1.dp,
                        if (state.errorMessage != null) PeakRed else HairlineBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.errorMessage != null) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = PeakRed
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PeakRed
                            )
                        } else {
                            Text(
                                text = state.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonCyan
                            )
                        }
                    }
                }
            }

            if (state.recentMessages.isNotEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = StudioSurface,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Live Chat Stream (${state.recentMessages.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            state.recentMessages.takeLast(6).reversed().forEach { msg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${msg.author}:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ViralYellow,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.capturedMoments.isNotEmpty()) {
                item {
                    Text(
                        text = "Detected Viral Moments",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                items(state.capturedMoments, key = { it.id }) { capture ->
                    LiveMomentCaptureCard(capture = capture)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LiveMomentCaptureCard(capture: LiveMomentCapture) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = StudioSurface,
        border = BorderStroke(1.dp, HypeOrange.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = capture.hookTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ViralYellow,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = HypeOrange.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${capture.viralScore}/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = HypeOrange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = capture.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = "${capture.chatVelocityPer30s} msgs/30s • Captured at ${formatter.format(Date(capture.capturedAtMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
            capture.sampleMessages.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

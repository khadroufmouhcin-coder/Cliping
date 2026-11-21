package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ClipStorageRepository
import com.example.firebase.configureAppCheck
import com.example.ui.screens.ClipPlayerDialog
import com.example.ui.screens.ClipStudioScreen
import com.example.ui.screens.ClipsVaultScreen
import com.example.ui.screens.LiveMonitorScreen
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.ViralClipperTheme
import com.example.viewmodel.ClipperViewModel

enum class StudioDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CLIP_STUDIO(
        labelRes = R.string.nav_clip_studio,
        selectedIcon = Icons.Filled.MovieCreation,
        unselectedIcon = Icons.Outlined.MovieCreation,
        testTag = "nav_tab_clip_studio"
    ),
    LIVE_MONITOR(
        labelRes = R.string.nav_live_monitor,
        selectedIcon = Icons.Filled.Podcasts,
        unselectedIcon = Icons.Outlined.Podcasts,
        testTag = "nav_tab_live_monitor"
    ),
    CLIPS_VAULT(
        labelRes = R.string.nav_clips_vault,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_tab_clips_vault"
    )
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ViralClipperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ViralClipperApp(widthSizeClass = windowSizeClass.widthSizeClass)
                }
            }
        }
    }
}

@Composable
fun ViralClipperApp(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: ClipperViewModel = viewModel()
) {
    val context = LocalContext.current
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(StudioDestination.CLIP_STUDIO) }
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    if (currentTab != StudioDestination.CLIP_STUDIO) {
        BackHandler {
            currentTab = StudioDestination.CLIP_STUDIO
        }
    }

    val shareClipAction: (com.example.model.SavedClip) -> Unit = { clip ->
        val intent = ClipStorageRepository.buildShareIntent(context, clip)
        context.startActivity(Intent.createChooser(intent, "Share 9:16 Viral Short"))
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!useRail) {
                NavigationBar(
                    containerColor = StudioSurface,
                    contentColor = Color.White
                ) {
                    StudioDestination.entries.forEach { dest ->
                        val selected = currentTab == dest
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = dest },
                            modifier = Modifier.testTag(dest.testTag),
                            icon = {
                                NavItemIcon(
                                    dest = dest,
                                    selected = selected,
                                    vaultCount = vaultState.clips.size,
                                    isLiveMonitoring = liveState.isMonitoring
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = ElectricViolet.copy(alpha = 0.28f),
                                unselectedIconColor = MutedSlate,
                                unselectedTextColor = MutedSlate
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useRail) {
                NavigationRail(
                    containerColor = StudioSurface,
                    contentColor = Color.White
                ) {
                    StudioDestination.entries.forEach { dest ->
                        val selected = currentTab == dest
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = dest },
                            modifier = Modifier.testTag(dest.testTag),
                            icon = {
                                NavItemIcon(
                                    dest = dest,
                                    selected = selected,
                                    vaultCount = vaultState.clips.size,
                                    isLiveMonitoring = liveState.isMonitoring
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = ElectricViolet.copy(alpha = 0.28f),
                                unselectedIconColor = MutedSlate,
                                unselectedTextColor = MutedSlate
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentTab) {
                    StudioDestination.CLIP_STUDIO -> {
                        ClipStudioScreen(
                            state = studioState,
                            onVideoPicked = viewModel::onVideoPicked,
                            onYoutubeUrlChanged = viewModel::updateYoutubeUrlInput,
                            onAnalyzeYoutubeUrl = viewModel::analyzeYouTubeUrl,
                            onSelectDuration = viewModel::selectDuration,
                            onStartSecChanged = viewModel::updateStartSec,
                            onSnapToPeak = {
                                studioState.energyProfile?.let { viewModel.updateStartSec(it.peakStartSec) }
                            },
                            onToggleVerticalBlur = viewModel::toggleVerticalBlur,
                            onToggleCaptions = viewModel::toggleCaptions,
                            onSelectCaptionStyle = viewModel::selectCaptionStyle,
                            onToggleHook = viewModel::toggleHook,
                            onHookTextChanged = viewModel::updateHookText,
                            onSuggestHookWithAi = viewModel::suggestHookWithAi,
                            onGenerateClip = viewModel::generateViralClip,
                            onPlayGeneratedClip = viewModel::openClipPlayer
                        )
                    }

                    StudioDestination.LIVE_MONITOR -> {
                        LiveMonitorScreen(
                            state = liveState,
                            onUrlChanged = viewModel::updateLiveUrl,
                            onDurationChanged = viewModel::updateLiveDuration,
                            onThresholdChanged = viewModel::updateLiveThreshold,
                            onMaxWaitChanged = viewModel::updateLiveMaxWait,
                            onCaptionStyleChanged = viewModel::selectLiveCaptionStyle,
                            onHookTextChanged = viewModel::updateLiveHookText,
                            onStartMonitoring = viewModel::startLiveMonitoring,
                            onStopMonitoring = viewModel::stopLiveMonitoring,
                            onCaptureMoment = viewModel::captureCurrentLiveMoment
                        )
                    }

                    StudioDestination.CLIPS_VAULT -> {
                        ClipsVaultScreen(
                            state = vaultState,
                            onPlayClip = viewModel::openClipPlayer,
                            onSaveToGallery = viewModel::saveClipToGallery,
                            onShareClip = shareClipAction,
                            onDeleteClip = viewModel::deleteClip,
                            onNavigateToStudio = { currentTab = StudioDestination.CLIP_STUDIO }
                        )
                    }
                }
            }
        }
    }

    vaultState.activePlayerClip?.let { clip ->
        ClipPlayerDialog(
            clip = clip,
            onDismiss = viewModel::closeClipPlayer,
            onSaveToGallery = viewModel::saveClipToGallery,
            onShare = shareClipAction
        )
    }
}

@Composable
private fun NavItemIcon(
    dest: StudioDestination,
    selected: Boolean,
    vaultCount: Int,
    isLiveMonitoring: Boolean
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    when (dest) {
        StudioDestination.CLIPS_VAULT -> {
            BadgedBox(
                badge = {
                    if (vaultCount > 0) {
                        Badge(containerColor = ElectricViolet, contentColor = Color.White) {
                            Text(vaultCount.toString())
                        }
                    }
                }
            ) {
                Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
            }
        }
        StudioDestination.LIVE_MONITOR -> {
            BadgedBox(
                badge = {
                    if (isLiveMonitoring) {
                        Badge(containerColor = Color(0xFFEF4444))
                    }
                }
            ) {
                Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
            }
        }
        else -> {
            Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
        }
    }
}

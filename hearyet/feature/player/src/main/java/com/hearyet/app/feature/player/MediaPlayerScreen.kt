package com.hearyet.app.feature.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.hearyet.app.core.common.extensions.isTelevision
import com.hearyet.app.core.model.ControlButtonsPosition
import com.hearyet.app.core.model.GuestInfo
import com.hearyet.app.core.model.PlayerPreferences
import com.hearyet.app.core.model.SessionHolder
import com.hearyet.app.core.model.SessionStartProvider
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.ui.R as coreUiR
import com.hearyet.app.core.ui.component.HostSessionPanel
import com.hearyet.app.core.ui.components.requestFocusUntilLanded
import com.hearyet.app.core.ui.components.thenIf
import com.hearyet.app.core.ui.extensions.copy
import com.hearyet.app.feature.player.buttons.NextButton
import com.hearyet.app.feature.player.buttons.PlayPauseButton
import com.hearyet.app.feature.player.buttons.PlayerButton
import com.hearyet.app.feature.player.buttons.PreviousButton
import com.hearyet.app.feature.player.state.ControlsVisibilityState
import com.hearyet.app.feature.player.state.VerticalGesture
import com.hearyet.app.feature.player.state.rememberBrightnessState
import com.hearyet.app.feature.player.state.rememberControlsVisibilityState
import com.hearyet.app.feature.player.state.rememberErrorState
import com.hearyet.app.feature.player.state.rememberMediaPresentationState
import com.hearyet.app.feature.player.state.rememberMetadataState
import com.hearyet.app.feature.player.state.rememberPictureInPictureState
import com.hearyet.app.feature.player.state.rememberRotationState
import com.hearyet.app.feature.player.state.rememberSeekGestureState
import com.hearyet.app.feature.player.state.rememberTapGestureState
import com.hearyet.app.feature.player.state.rememberVideoZoomAndContentScaleState
import com.hearyet.app.feature.player.state.rememberVolumeAndBrightnessGestureState
import com.hearyet.app.feature.player.state.rememberVolumeState
import com.hearyet.app.feature.player.extensions.formatted
import com.hearyet.app.feature.player.extensions.nameRes
import com.hearyet.app.feature.player.state.seekAmountFormatted
import com.hearyet.app.feature.player.state.seekToPositionFormated
import com.hearyet.app.feature.player.ui.DoubleTapIndicator
import com.hearyet.app.feature.player.ui.OverlayShowView
import com.hearyet.app.feature.player.ui.OverlayView
import com.hearyet.app.feature.player.ui.SubtitleConfiguration
import com.hearyet.app.feature.player.ui.VerticalProgressView
import com.hearyet.app.feature.player.ui.controls.ControlsBottomView
import com.hearyet.app.feature.player.ui.controls.ControlsTopView
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

val LocalControlsVisibilityState = compositionLocalOf<ControlsVisibilityState?> { null }

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    player: Player?,
    viewModel: PlayerViewModel,
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit,
    onBackClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
) {
    val volumeState = rememberVolumeState(
        player = player,
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )
    player ?: return
    val metadataState = rememberMetadataState(player)
    val mediaPresentationState = rememberMediaPresentationState(player)
    val controlsVisibilityState = rememberControlsVisibilityState(
        player = player,
        hideAfter = playerPreferences.controllerAutoHideTimeout.seconds,
    )
    val tapGestureState = rememberTapGestureState(
        player = player,
        doubleTapGesture = playerPreferences.doubleTapGesture,
        seekIncrementMillis = playerPreferences.seekIncrement.seconds.inWholeMilliseconds,
        useLongPressGesture = playerPreferences.useLongPressControls,
        longPressSpeed = playerPreferences.longPressControlsSpeed,
    )
    val seekGestureState = rememberSeekGestureState(
        player = player,
        sensitivity = playerPreferences.seekSensitivity,
        enableSeekGesture = playerPreferences.useSeekControls,
    )
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        autoEnter = playerPreferences.autoPip,
    )
    val videoZoomAndContentScaleState = rememberVideoZoomAndContentScaleState(
        player = player,
        initialContentScale = playerPreferences.playerVideoZoom,
        enableZoomGesture = playerPreferences.useZoomControls,
        enablePanGesture = playerPreferences.enablePanGesture,
        onEvent = viewModel::onVideoZoomEvent,
    )
    val brightnessState = rememberBrightnessState()
    val volumeAndBrightnessGestureState = rememberVolumeAndBrightnessGestureState(
        volumeState = volumeState,
        brightnessState = brightnessState,
        enableVolumeGesture = playerPreferences.enableVolumeSwipeGesture,
        enableBrightnessGesture = playerPreferences.enableBrightnessSwipeGesture,
        volumeGestureSensitivity = playerPreferences.volumeGestureSensitivity,
        brightnessGestureSensitivity = playerPreferences.brightnessGestureSensitivity,
    )
    val rotationState = rememberRotationState(
        player = player,
        screenOrientation = playerPreferences.playerScreenOrientation,
    )
    val errorState = rememberErrorState(player = player)

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
        }
    }

    LaunchedEffect(tapGestureState.isLongPressGestureInAction) {
        if (tapGestureState.isLongPressGestureInAction) {
            controlsVisibilityState.hideControls()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (playerPreferences.rememberPlayerBrightness) {
            brightnessState.setBrightness(playerPreferences.playerBrightness)
        }
    }

    LaunchedEffect(brightnessState.currentBrightness) {
        if (playerPreferences.rememberPlayerBrightness) {
            viewModel.updatePlayerBrightness(brightnessState.currentBrightness)
        }
    }

    var overlayView by remember { mutableStateOf<OverlayView?>(null) }

    // FE §9.6 — in-player Host session side sheet: reachable from the player
    // controls whether the session was started here or from Home's Create card.
    var showSessionPanel by remember { mutableStateOf(false) }
    val activeSession = SessionHolder.active
    val activeSessionState by (activeSession?.sessionState?.collectAsState()
        ?: remember { mutableStateOf<SessionState?>(null) })
    val hostGuestCount by (activeSession?.hostGuestCount?.collectAsState()
        ?: remember { mutableStateOf(0) })
    val hostGuests by (activeSession?.hostGuests?.collectAsState()
        ?: remember { mutableStateOf(emptyList<GuestInfo>()) })
    val isHostSessionActive = activeSession?.isHost == true &&
        activeSessionState != null &&
        activeSessionState !is SessionState.Ended &&
        activeSessionState !is SessionState.Idle
    // §9.6 — aggregate sync health in the controls pill (worst connected guest).
    val hostSyncHealth: SyncHealth? = when (activeSessionState) {
        is SessionState.Playing, is SessionState.Connected -> SyncHealth.GOOD
        is SessionState.Error -> SyncHealth.POOR
        else -> null
    }
    // §18 — session duration for the panel header (first guest connect, else creation).
    val sessionStartedAtMs = hostGuests.minOfOrNull { it.connectedAtMs }
        ?: if (activeSessionState is SessionState.Advertising) System.currentTimeMillis() else 0L

    // FE §9.6 — QR bitmap for the session panel
    val qrBitmap = remember(activeSession?.qrPayload) {
        activeSession?.qrPayload?.let { payload ->
            val size = 512
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val bitmap = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bitmap.asImageBitmap()
        }
    }

    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    /** BE §12 — true while the Host is actively streaming to at least one guest.
     *  Playback-speed control is cut from session context. */
    fun isHostSessionWithGuests(): Boolean {
        val session = SessionHolder.active ?: return false
        if (!session.isHost) return false
        return session.sessionState.value is SessionState.Playing && session.hostGuestCount.value > 0
    }
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }
    var isPlayPauseFocused by remember { mutableStateOf(false) }
    var isUnlockFocused by remember { mutableStateOf(false) }
    val seekIncrementMs = playerPreferences.seekIncrement.seconds.inWholeMilliseconds

    if (isTv) {
        LaunchedEffect(controlsVisibilityState.controlsVisible, controlsVisibilityState.controlsLocked, overlayView) {
            if (overlayView != null) return@LaunchedEffect
            if (!controlsVisibilityState.controlsVisible) {
                runCatching { rootFocusRequester.requestFocus() }
                return@LaunchedEffect
            }
            val locked = controlsVisibilityState.controlsLocked
            val target = if (locked) unlockFocusRequester else playPauseFocusRequester
            target.requestFocusUntilLanded(attempts = 20) { if (locked) isUnlockFocused else isPlayPauseFocused }
        }
    }

    // D-pad seeking (controls hidden): accumulate the skipped amount and briefly show it.
    var dpadSeekOffsetMs by remember { mutableLongStateOf(0L) }
    var dpadSeekTargetMs by remember { mutableLongStateOf(0L) }
    var dpadSeekActive by remember { mutableStateOf(false) }
    var dpadSeekTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(dpadSeekTick) {
        if (!dpadSeekActive) return@LaunchedEffect
        delay(1.seconds)
        dpadSeekActive = false
    }

    val showDpadSeekFeedback: (Long) -> Unit = { deltaMs ->
        if (!dpadSeekActive) dpadSeekOffsetMs = 0L
        dpadSeekOffsetMs += deltaMs
        dpadSeekTargetMs = player.currentPosition
        dpadSeekActive = true
        dpadSeekTick++
    }

    CompositionLocalProvider(LocalControlsVisibilityState provides controlsVisibilityState) {
        Box {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRequester(rootFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent { keyEvent ->
                                    if (overlayView != null) {
                                        false
                                    } else {
                                        handlePlayerKeyEvent(
                                            keyEvent = keyEvent,
                                            player = player,
                                            controls = controlsVisibilityState,
                                            seekIncrementMs = seekIncrementMs,
                                            isPlayPauseFocused = isPlayPauseFocused,
                                            onDpadSeek = showDpadSeekFeedback,
                                        )
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                PlayerContentFrame(
                    player = player,
                    pictureInPictureState = pictureInPictureState,
                    controlsVisibilityState = controlsVisibilityState,
                    tapGestureState = tapGestureState,
                    seekGestureState = seekGestureState,
                    videoZoomAndContentScaleState = videoZoomAndContentScaleState,
                    volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
                    subtitleConfiguration = SubtitleConfiguration(
                        useSystemCaptionStyle = playerPreferences.useSystemCaptionStyle,
                        showBackground = playerPreferences.subtitleBackground,
                        font = playerPreferences.subtitleFont,
                        textSize = playerPreferences.subtitleTextSize,
                        textBold = playerPreferences.subtitleTextBold,
                        applyEmbeddedStyles = playerPreferences.applyEmbeddedStyles,
                    ),
                )

                AnimatedVisibility(
                    visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                }

                if (mediaPresentationState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp),
                    )
                }

                DoubleTapIndicator(tapGestureState = tapGestureState)

                DpadSeekIndicator(
                    visible = dpadSeekActive && dpadSeekOffsetMs != 0L,
                    offsetMs = dpadSeekOffsetMs,
                    positionMs = dpadSeekTargetMs,
                )

                AnimatedVisibility(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .align(Alignment.TopCenter),
                    visible = tapGestureState.isLongPressGestureInAction,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(shape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ),
                        ) {
                            Text(
                                text = stringResource(coreUiR.string.fast_playback_speed, tapGestureState.longPressSpeed),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                if (controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(top = 24.dp),
                    ) {
                        PlayerButton(
                            modifier = Modifier.thenIf(isTv) {
                                focusRequester(unlockFocusRequester)
                                    .onFocusChanged { isUnlockFocused = it.hasFocus }
                            },
                            containerColor = Color.Black.copy(0.5f),
                            onClick = { controlsVisibilityState.unlockControls() }
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_lock),
                                contentDescription = stringResource(coreUiR.string.controls_unlock),
                            )
                        }
                    }
                } else {
                    PlayerControlsView(
                        topView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.controlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ControlsTopView(
                                    title = metadataState.title ?: "",
                                    hostSessionActive = isHostSessionActive,
                                    hostGuestCount = hostGuestCount,
                                    hostSyncHealth = hostSyncHealth,
                                    onAudioClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.AUDIO_SELECTOR
                                    },
                                    onSubtitleClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.SUBTITLE_SELECTOR
                                    },
                                    onPlaybackSpeedClick = {
                                        controlsVisibilityState.hideControls()
                                        // BE §12 — playback speed is cut from session context:
                                        // changing speed mid-session would desync every guest.
                                        if (!isHostSessionWithGuests()) {
                                            overlayView = OverlayView.PLAYBACK_SPEED
                                        }
                                    },
                                    onPlaylistClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.PLAYLIST
                                    },
                                    // BE §2.1 / FE §9.6 — start a Host session from the
                                    // player, or open the session side sheet if one is
                                    // already active. One consistent entry point.
                                    onSessionClick = {
                                        controlsVisibilityState.hideControls()
                                        val active = SessionHolder.active
                                        if (active == null ||
                                            active.sessionState.value is SessionState.Ended
                                        ) {
                                            val app = context.applicationContext as? SessionStartProvider
                                            if (app != null) {
                                                app.startHostSession(android.os.Build.MODEL ?: "Android")
                                            }
                                        }
                                        showSessionPanel = true
                                    },
                                    onBackClick = onBackClick,
                                )
                            }
                        },
                        middleView = {
                            when {
                                seekGestureState.seekAmount != null -> InfoView(info = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]")
                                videoZoomAndContentScaleState.isZooming -> InfoView(info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%")
                                videoZoomAndContentScaleState.showContentScaleIndicator -> InfoView(info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()))
                                controlsVisibilityState.controlsVisible -> ControlsMiddleView(
                                    player = player,
                                    playPauseModifier = Modifier.thenIf(isTv) {
                                        focusRequester(playPauseFocusRequester)
                                            .onFocusChanged { isPlayPauseFocused = it.hasFocus }
                                    },
                                )
                                else -> Unit
                            }
                        },
                        bottomView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                val context = LocalContext.current
                                ControlsBottomView(
                                    player = player,
                                    mediaPresentationState = mediaPresentationState,
                                    controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                                        ControlButtonsPosition.LEFT -> Alignment.Start
                                        ControlButtonsPosition.RIGHT -> Alignment.End
                                    },
                                    videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                    isPipSupported = pictureInPictureState.isPipSupported,
                                    seekBarModifier = Modifier.thenIf(isTv) {
                                        focusRequester(seekBarFocusRequester)
                                            .focusProperties { up = playPauseFocusRequester }
                                    },
                                    onSeek = seekGestureState::onSeek,
                                    onSeekEnd = seekGestureState::onSeekEnd,
                                    onRotateClick = rotationState::rotate,
                                    onPlayInBackgroundClick = onPlayInBackgroundClick,
                                    onLockControlsClick = {
                                        controlsVisibilityState.showControls()
                                        controlsVisibilityState.lockControls()
                                    },
                                    onVideoContentScaleClick = {
                                        controlsVisibilityState.showControls()
                                        videoZoomAndContentScaleState.switchToNextVideoContentScale()
                                    },
                                    onVideoContentScaleLongClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.VIDEO_CONTENT_SCALE
                                    },
                                    onPictureInPictureClick = {
                                        if (!pictureInPictureState.hasPipPermission) {
                                            Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                            pictureInPictureState.openPictureInPictureSettings()
                                        } else {
                                            pictureInPictureState.enterPictureInPictureMode()
                                        }
                                    },
                                )
                            }
                        },
                    )
                }

                val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .padding(systemBarsPadding.copy(top = 0.dp, bottom = 0.dp))
                        .padding(24.dp),
                ) {
                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterStart),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = volumeState.volumePercentage,
                            maxValue = volumeState.maxVolumePercentage,
                            icon = painterResource(coreUiR.drawable.ic_volume),
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = brightnessState.brightnessPercentage,
                            icon = painterResource(coreUiR.drawable.ic_brightness),
                        )
                    }
                }
            }

            OverlayShowView(
                player = player,
                overlayView = overlayView,
                videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                onDismiss = { overlayView = null },
                onSelectSubtitleClick = onSelectSubtitleClick,
                onSubtitleOptionEvent = viewModel::onSubtitleOptionEvent,
                onVideoContentScaleChanged = { videoZoomAndContentScaleState.onVideoContentScaleChanged(it) },
            )

            // FE §9.6 — in-player Host session side sheet (slides in over the dimmed
            // player, contains the collapsed QR/code header, the full guest list with
            // per-guest sync dots + drift, and an "End session" action behind a
            // ConfirmationDialog). Dismiss by scrim tap or Back.
            HostSessionPanel(
                visible = showSessionPanel,
                sessionCode = activeSession?.sessionCode,
                guests = hostGuests,
                qrBitmap = qrBitmap,
                sessionStartedAtMs = sessionStartedAtMs,
                onDismiss = { showSessionPanel = false },
                onEndSession = {
                    showSessionPanel = false
                    SessionHolder.active?.endSession()
                },
            )
        }
    }

    errorState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = stringResource(coreUiR.string.error_playing_video))
            },
            text = {
                Text(text = error.message ?: stringResource(coreUiR.string.unknown_error))
            },
            confirmButton = {
                if (player.hasNextMediaItem()) {
                    TextButton(
                        onClick = {
                            errorState.dismiss()
                            player.seekToNext()
                            player.play()
                        },
                    ) {
                        Text(text = stringResource(coreUiR.string.play_next_video))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        errorState.dismiss()
                        onBackClick()
                    },
                ) {
                    Text(text = stringResource(coreUiR.string.exit))
                }
            },
        )
    }

    BackHandler {
        when {
            showSessionPanel -> showSessionPanel = false
            overlayView != null -> overlayView = null
            isTv && controlsVisibilityState.controlsVisible -> controlsVisibilityState.hideControls()
            else -> onBackClick()
        }
    }
}

@Composable
fun InfoView(
    modifier: Modifier = Modifier,
    info: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = info,
            style = textStyle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shows the cumulative amount skipped by repeated D-pad left/right seeks while the controls are
 * hidden, along with the resulting position. Fades out shortly after the last seek.
 */
@Composable
fun BoxScope.DpadSeekIndicator(
    visible: Boolean,
    offsetMs: Long,
    positionMs: Long,
) {
    AnimatedVisibility(
        modifier = Modifier.align(Alignment.Center),
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_fast),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(if (offsetMs < 0) 180f else 0f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${if (offsetMs >= 0) "+" else "-"}${abs(offsetMs).milliseconds.inWholeSeconds}s",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = positionMs.milliseconds.formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun ControlsMiddleView(
    modifier: Modifier = Modifier,
    player: Player,
    playPauseModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviousButton(player = player)
        PlayPauseButton(player = player, modifier = playPauseModifier)
        NextButton(player = player)
    }
}

@OptIn(UnstableApi::class)
private fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    player: Player,
    controls: ControlsVisibilityState,
    seekIncrementMs: Long,
    isPlayPauseFocused: Boolean,
    onDpadSeek: (deltaMs: Long) -> Unit,
): Boolean {
    if (keyEvent.key == Key.Back && !controls.controlsLocked) {
        if (!controls.controlsVisible) return false // controls already hidden: let BACK exit
        if (keyEvent.type == KeyEventType.KeyUp) controls.hideControls()
        return true
    }
    if (keyEvent.type != KeyEventType.KeyDown) return false
    if (controls.controlsLocked) {
        return when (keyEvent.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (controls.controlsVisible) controls.unlockControls() else controls.showControls()
                true
            }
            else -> {
                controls.showControls()
                false
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
        player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    return when (keyEvent.key) {
        Key.MediaPlayPause, Key.Spacebar -> { togglePlayPause(); controls.showControls(); true }
        Key.MediaPlay -> { player.play(); controls.showControls(); true }
        Key.MediaPause -> { player.pause(); controls.showControls(); true }
        Key.MediaFastForward -> { seekBy(seekIncrementMs); controls.showControls(); true }
        Key.MediaRewind -> { seekBy(-seekIncrementMs); controls.showControls(); true }
        Key.MediaNext -> { player.seekToNext(); controls.showControls(); true }
        Key.MediaPrevious -> { player.seekToPrevious(); controls.showControls(); true }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            when {
                !controls.controlsVisible -> {
                    controls.showControls()
                    true
                }
                isPlayPauseFocused -> {
                    togglePlayPause()
                    controls.showControls()
                    true
                }
                else -> false
            }
        }
        Key.DirectionLeft -> {
            if (!controls.controlsVisible) {
                seekBy(-seekIncrementMs)
                onDpadSeek(-seekIncrementMs)
                true
            } else {
                controls.showControls()
                false
            }
        }
        Key.DirectionRight -> {
            if (!controls.controlsVisible) {
                seekBy(seekIncrementMs)
                onDpadSeek(seekIncrementMs)
                true
            } else {
                controls.showControls()
                false
            }
        }
        Key.DirectionUp, Key.DirectionDown -> {
            if (!controls.controlsVisible) {
                controls.showControls()
                true
            } else {
                controls.showControls()
                false
            }
        }
        else -> false
    }
}

@Composable
fun PlayerControlsView(
    modifier: Modifier = Modifier,
    topView: @Composable () -> Unit,
    middleView: @Composable BoxScope.() -> Unit,
    bottomView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            topView()
            Spacer(modifier = Modifier.weight(1f))
            bottomView()
        }

        middleView()
    }
}

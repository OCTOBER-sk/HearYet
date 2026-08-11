package com.hearyet.app.navigation

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.hearyet.app.HearYetApplication
import com.hearyet.app.R
import com.hearyet.app.core.model.RecentActivityEntry
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.model.SessionHolder
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.model.GuestInfo
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.feature.session.ended.SessionEndedScreen
import com.hearyet.app.feature.session.guest.GuestSessionScreen
import com.hearyet.app.feature.session.host.InSessionHostScreen
import com.hearyet.app.feature.permission.HearYetPermission
import com.hearyet.app.feature.permission.PermissionRequiredScreen
import com.hearyet.app.feature.permission.nearbyRuntimePermissions
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.feature.home.HomeScreen
import com.hearyet.app.feature.onboarding.OnboardingScreen
import com.hearyet.app.feature.session.create.CreateSessionSheet
import com.hearyet.app.feature.session.join.JoinNameEntryScreen
import com.hearyet.app.feature.session.join.JoinSessionScreen
import com.hearyet.app.feature.session.join.JoinScreenMode
import com.hearyet.app.feature.videopicker.navigation.MediaPickerRoute
import com.hearyet.app.settings.navigation.AboutPreferencesRoute
import com.hearyet.app.settings.navigation.SettingsRoute
import com.hearyet.app.settings.navigation.navigateToSettings
import com.hearyet.app.core.model.SessionError
import com.hearyet.app.qr.QrGenerator
import com.hearyet.app.sync.SessionCoordinator
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object SplashRoute : NavKey

@Serializable
object OnboardingRoute : NavKey

@Serializable
object HomeRoute : NavKey

@Serializable
object JoinRoute : NavKey

@Serializable
object InSessionGuestRoute : NavKey

@Serializable
object InSessionHostRoute : NavKey

@Serializable
object SessionEndedRoute : NavKey

@Serializable
data class PermissionRequiredRoute(
    val permission: String = "CAMERA",
) : NavKey

internal fun startupRoute(hasCompletedOnboarding: Boolean): NavKey =
    if (hasCompletedOnboarding) HomeRoute else OnboardingRoute

@OptIn(ExperimentalMaterial3Api::class)
fun EntryProviderScope<NavKey>.hearYetNavGraph(
    reduceMotion: Boolean,
    onOnboardingFinished: () -> Unit,
    onRecentActivity: (RecentActivityEntry) -> Unit,
    backStack: NavBackStack<NavKey>,
) {
    entry<SplashRoute>(metadata = navigationMetadata(reduceMotion)) {
        // FE §9.1 — App-cover logo (launcher foreground) centered on Background, no text, no spinner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HearYetColors.Background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "HearYet",
                modifier = Modifier.size(128.dp),
            )
        }
    }
    entry<OnboardingRoute>(metadata = navigationMetadata(reduceMotion)) {
        // FE §7 — screen 5's "Learn more" links to the full credit in About (FE §9.9),
        // pushed on the same back stack so back returns through Settings to onboarding.
        OnboardingScreen(
            onNavigateToHome = onOnboardingFinished,
            onLearnMore = {
                backStack.add(SettingsRoute)
                backStack.add(AboutPreferencesRoute)
            },
        )
    }
    entry<HomeRoute>(metadata = navigationMetadata(reduceMotion)) {
        // ── Create Session bottom sheet state ────────────────────────
        // rememberSaveable so the sheet survives a trip to the media library
        // (HomeRoute's composition is disposed while the picker is on top).
        var showCreateSheet by rememberSaveable { mutableStateOf(false) }

        // BE §1 — contextual permission request for Create (BT + Nearby), requested
        // only when Create is tapped. Denial routes to the permission-required screen.
        val createPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            if (grants.values.all { it }) {
                showCreateSheet = true
            } else {
                backStack.add(PermissionRequiredRoute(permission = "NEARBY"))
            }
        }

        // C11 — SessionCoordinator scoped to HomeRoute (not sheet visibility)
        // so it survives the Create-sheet → PlayerActivity transition. When we
        // return from the media library with the sheet still open, adopt the
        // still-live host session instead of orphaning it with a new coordinator.
        val context = LocalContext.current
        val coordinator = remember {
            val existing = SessionHolder.active as? SessionCoordinator
            if (existing != null && existing.isHost) {
                val live = when (existing.sessionState.value) {
                    is SessionState.Advertising, is SessionState.WaitingForMedia,
                    is SessionState.Connected, is SessionState.Playing,
                    -> true
                    else -> false
                }
                if (live) existing else SessionCoordinator(context)
            } else {
                SessionCoordinator(context)
            }
        }
        coordinator.recentActivitySink = onRecentActivity

        // Home guide cards — dismissed set persisted via DataStore-backed prefs.
        val app = context.applicationContext as? HearYetApplication
        val scope = rememberCoroutineScope()
        val appPreferences by (app?.applicationPreferences?.collectAsState()
            ?: remember { mutableStateOf(com.hearyet.app.core.model.ApplicationPreferences()) })
        val guideCardsDismissed = appPreferences.dismissedGuideCards.toSet()

        // BE §10.2 — Restore persisted session on app restart.
        // Guest: start discovery + RejoinRequest, then navigate to in-session screen.
        // Host: clear persisted state and stay on Home (can't silently resume).
        LaunchedEffect(Unit) {
            if (coordinator.tryRestoreSession()) {
                if (coordinator.isHost) {
                    // Host cannot silently resume after process death — clear and stay Home
                    coordinator.clearSessionState()
                } else {
                    // Guest — perform rejoin and navigate to in-session screen
                    coordinator.performRestoredGuestRejoin()
                    SessionHolder.active = coordinator
                    backStack.replaceRoot(InSessionGuestRoute)
                }
            }
        }

        // ── Media picked from the library for the Create flow. ──────
        // Restored from SessionMediaPickHolder when returning from the picker,
        // then held in rememberSaveable while the sheet is open.
        val restoredMediaPick = com.hearyet.app.SessionMediaPickHolder.consumePickedUri()
        var selectedMediaUri by rememberSaveable { mutableStateOf(restoredMediaPick) }

        // ── URL entry dialog state
        var showUrlDialog by rememberSaveable { mutableStateOf(false) }
        var urlInput by rememberSaveable { mutableStateOf("") }

        // ── Start hosting when sheet opens (FE §9.4). Idempotent: when the
        // sheet reopens after returning from the library, the adopted
        // coordinator is already advertising — don't start a second session.
        if (showCreateSheet) {
            LaunchedEffect(Unit) {
                if (coordinator.sessionState.value == SessionState.Idle) {
                    coordinator.startAsHost(android.os.Build.MODEL ?: "Android")
                }
                // C11 — Store in SessionHolder so PlayerActivity can observe session state
                (context.applicationContext as? com.hearyet.app.HearYetApplication)
                    ?.setActiveSession(coordinator)
            }

            // Generate QR bitmap from the coordinator's payload
            val qrBitmap = remember(coordinator.qrPayload) {
                coordinator.qrPayload?.let { payload ->
                    QrGenerator.generate(payload).asImageBitmap()
                }
            }

            // Collect state from coordinator for live UI updates
            val sessionState by coordinator.sessionState.collectAsState()
            val guestCount by coordinator.hostGuestCount.collectAsState()

            CreateSessionSheet(
                qrBitmap = qrBitmap,
                sessionCode = coordinator.sessionCode ?: "------",
                sessionState = sessionState,
                guestCount = guestCount,
                hasMediaPicked = selectedMediaUri != null,
                onDismiss = {
                    coordinator.cancelSessionCreation()
                    showCreateSheet = false
                    selectedMediaUri = null
                },
                onPickMedia = {
                    // Open the in-app media library; the picked file is routed
                    // back through SessionMediaPickHolder (see MediaNavGraph).
                    com.hearyet.app.SessionMediaPickHolder.pendingPick = { uri ->
                        try {
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(uri, flags)
                        } catch (_: Exception) {
                            // Not a persistable URI — playback still works for this session.
                        }
                        // Adopt the pick immediately: if HomeRoute stayed composed while
                        // the picker was on top, rememberSaveable's initializer below has
                        // already run, so consumePickedUri() alone would drop the URI and
                        // the sheet would keep saying "Pick a file".
                        selectedMediaUri = uri
                        com.hearyet.app.SessionMediaPickHolder.pickedUri = uri
                        backStack.removeLastIfNotRoot()
                    }
                    backStack.add(MediaPickerRoute())
                },
                onPlayFromUrl = {
                    showUrlDialog = true
                },
                onStartPlayback = {
                    val playUri = selectedMediaUri
                    if (playUri != null) {
                        coordinator.onMediaStarted(0)
                        showCreateSheet = false
                        // C11 — Coordinator stays alive in Application; PlayerActivity will read it
                        // Start the player with the selected media (reuses MediaNavGraph's startPlayback)
                        context.startPlayback(playUri, grantReadPermission = true)
                    }
                },
            )

            // ── URL entry dialog
            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    title = {
                        Text(
                            text = "Play from URL",
                            color = HearYetColors.OnBackground,
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Enter a direct link to a video or audio file.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HearYetColors.OnSurfaceMuted,
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val trimmed = urlInput.trim()
                                        if (trimmed.isNotEmpty()) {
                                            selectedMediaUri = Uri.parse(trimmed)
                                            showUrlDialog = false
                                            urlInput = ""
                                        }
                                    },
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = HearYetColors.OnBackground,
                                    unfocusedTextColor = HearYetColors.OnBackground,
                                    focusedBorderColor = HearYetColors.Accent,
                                    unfocusedBorderColor = HearYetColors.SurfaceOutline,
                                    focusedLabelColor = HearYetColors.Accent,
                                    unfocusedLabelColor = HearYetColors.OnSurfaceMuted,
                                    cursorColor = HearYetColors.Accent,
                                ),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmed = urlInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    selectedMediaUri = Uri.parse(trimmed)
                                    showUrlDialog = false
                                    urlInput = ""
                                }
                            },
                            enabled = urlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HearYetColors.Accent,
                                contentColor = HearYetColors.OnPrimary,
                            ),
                        ) {
                            Text("Add URL")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text(
                                text = "Cancel",
                                color = HearYetColors.OnSurfaceMuted,
                            )
                        }
                    },
                    containerColor = HearYetColors.SurfaceRaised,
                )
            }
        }

        // ── Home screen ──────────────────────────────────────────────
        // C11 — Read active session state for the Home banner (FE §9.3)
        val activeSession = SessionHolder.active
        val activeSessionState by (activeSession?.sessionState?.collectAsState()
            ?: remember { mutableStateOf<SessionState?>(null) })
        val isHostActive = !showCreateSheet &&
            activeSession?.isHost == true &&
            activeSessionState !is SessionState.Ended &&
            activeSessionState !is SessionState.Idle &&
            activeSessionState != null
        val isGuestActive = activeSession?.isHost == false &&
            activeSessionState !is SessionState.Ended &&
            activeSessionState !is SessionState.Idle &&
            activeSessionState != null
        val activeSyncHealth: SyncHealth? = when (activeSessionState) {
            is SessionState.Playing, is SessionState.Connected -> SyncHealth.GOOD
            is SessionState.Error -> SyncHealth.POOR
            else -> null
        }

        HomeScreen(
            onWatchClick = { backStack.add(MediaPickerRoute()) },
            onCreateClick = { createPermissionLauncher.launch(nearbyRuntimePermissions()) },
            onJoinClick = { backStack.add(JoinRoute) },            onSettingsClick = backStack::navigateToSettings,
            onReturnToSessionClick = {
                // C11 — Navigate back to in-session screen based on role (FE §9.3)
                if (activeSession?.isHost == true) {
                    backStack.replaceRoot(InSessionHostRoute)
                } else {
                    backStack.replaceRoot(InSessionGuestRoute)
                }
            },
            isHostInActiveSession = isHostActive,
            isGuestInActiveSession = isGuestActive,
            activeSessionSyncHealth = activeSyncHealth,
            guideCardsDismissed = guideCardsDismissed,
            onDismissGuideCard = { type ->
                scope.launch {
                    app?.preferencesRepository?.updateApplicationPreferences { prefs ->
                        prefs.copy(
                            dismissedGuideCards = (prefs.dismissedGuideCards + type).distinct(),
                        )
                    }
                }
            },
        )
    }
    entry<JoinRoute>(metadata = navigationMetadata(reduceMotion)) {
        // C9.4 — Join flow: name entry → scanner/code entry → state ladder.
        // Fully wired to SessionCoordinator per BE §4 / FE §9.5.
        val context = LocalContext.current
        val coordinator = remember { SessionCoordinator(context) }
        coordinator.recentActivitySink = onRecentActivity

        // BE §1 — request Join permissions (camera + nearby) the moment Join is tapped,
        // before the scanner opens or discovery starts. Denial routes to the
        // permission-required screen; the flow resumes on return.
        val joinPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            if (!grants.values.all { it }) {
                val missing =
                    if (grants[android.Manifest.permission.CAMERA] != true) "CAMERA" else "NEARBY"
                backStack.add(PermissionRequiredRoute(permission = missing))
            }
        }
        LaunchedEffect(Unit) {
            joinPermissionLauncher.launch(
                arrayOf(android.Manifest.permission.CAMERA) + nearbyRuntimePermissions()
            )
        }

        var nameEntered by remember { mutableStateOf(false) }
        // FE §9.5 — pre-fill with the DataStore-persisted name from a previous join,
        // falling back to the device model so the field is never blank.
        val app = context.applicationContext as? HearYetApplication
        val scope = rememberCoroutineScope()
        var displayName by remember {
            mutableStateOf(
                app?.applicationPreferences?.value?.guestDisplayName
                    ?: (android.os.Build.MODEL ?: "Android"),
            )
        }
        var screenMode by remember { mutableStateOf(JoinScreenMode.Scanning) }

        // Collect real session state from coordinator
        val sessionState by coordinator.sessionState.collectAsState()

        // Derive screen mode + error from coordinator state
        val errorReason = (sessionState as? SessionState.Error)?.reason
        val errorDetail = (sessionState as? SessionState.Error)?.detail

        // React to coordinator state transitions for UI mode
        LaunchedEffect(sessionState) {
            when (sessionState) {
                is SessionState.Discovering -> screenMode = JoinScreenMode.Connecting
                is SessionState.ClockSyncing -> screenMode = JoinScreenMode.Connecting
                is SessionState.Connected -> {
                    // C11 — Navigate to in-session Guest screen (FE §9.6)
                    SessionHolder.active = coordinator
                    backStack.replaceRoot(InSessionGuestRoute)
                }
                is SessionState.Playing -> {
                    // C11 — Navigate to in-session Guest screen when playback starts
                    SessionHolder.active = coordinator
                    backStack.replaceRoot(InSessionGuestRoute)
                }
                is SessionState.Error -> screenMode = JoinScreenMode.Error
                is SessionState.Idle -> { /* initial — keep current screenMode */ }
                is SessionState.Ended -> {
                    backStack.replaceRoot(SessionEndedRoute)
                }
                else -> {}
            }
        }

        // Cleanup on back-navigation out of the Join flow. Only tear down while the
        // guest is still mid-join — once it reached Connected/Playing, the session is
        // owned by the in-session screen and must survive this entry's disposal when
        // we replaceRoot to InSessionGuestRoute (BE §4).
        DisposableEffect(Unit) {
            onDispose {
                if (sessionState is SessionState.Discovering ||
                    sessionState is SessionState.ClockSyncing
                ) {
                    coordinator.leaveSession()
                }
            }
        }

        if (!nameEntered) {
            JoinNameEntryScreen(
                defaultName = displayName,
                onContinue = { name ->
                    displayName = name
                    coordinator.setDisplayName(name)
                    // FE §9.5 — persist locally (DataStore) and reuse on future joins.
                    app?.let { it ->
                        scope.launch {
                            it.preferencesRepository.updateApplicationPreferences { prefs ->
                                prefs.copy(guestDisplayName = name)
                            }
                        }
                    }
                    nameEntered = true
                },
            )
        } else {
            // FE §9.5 — the scanner, code entry, state ladder and error states
            // slide up in a bottom sheet over the name screen (smooth, M3)
            // instead of a full-screen route push. Swipe-down cancels the join;
            // the route's DisposableEffect tears down any half-open connection.
            val joinSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { backStack.removeLastIfNotRoot() },
                sheetState = joinSheetState,
                containerColor = HearYetColors.SurfaceRaised,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(modifier = Modifier.fillMaxHeight(0.88f)) {
                    JoinSessionScreen(
                        sessionState = sessionState,
                        screenMode = screenMode,
                        errorReason = errorReason,
                        errorDetail = errorDetail,
                        onQrDecoded = { raw ->
                            screenMode = JoinScreenMode.Connecting
                            coordinator.onQrScanned(raw)
                        },
                        onCodeEntered = { code ->
                            screenMode = JoinScreenMode.Connecting
                            coordinator.onCodeEntered(code)
                        },
                        onSwitchToCodeEntry = { screenMode = JoinScreenMode.CodeEntry },
                        onSwitchToScanning = { screenMode = JoinScreenMode.Scanning },
                        onRetry = {
                            coordinator.resetToIdle()
                            screenMode = JoinScreenMode.Scanning
                            if (errorReason == SessionError.PERMISSION_MISSING) {
                                // FE §9.11 — "Grant access" routes to the shared permission-required
                                // screen (which owns the runtime request and deep-links to app
                                // Settings when permanently denied) instead of an inline re-request.
                                val missing = if (
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    "CAMERA"
                                } else {
                                    "NEARBY"
                                }
                                backStack.add(PermissionRequiredRoute(permission = missing))
                            }
                        },
                        onBackToHome = {
                            coordinator.resetToIdle()
                            backStack.replaceRoot(HomeRoute)
                        },
                    )
                }
            }
        }
    }
    entry<InSessionGuestRoute>(metadata = navigationMetadata(reduceMotion)) {
        // C11 — Guest in-session UI (FE §9.6)
        val sessionHandle = SessionHolder.active
        val sessionState by (sessionHandle?.sessionState?.collectAsState()
            ?: remember { mutableStateOf<SessionState?>(null) })

        // BE §10 — when the host ends the session or becomes unreachable,
        // navigate to the explicit Session Ended screen (FE §9.8) instead
        // of leaving the guest on a frozen/stale "Listening in sync" screen.
        LaunchedEffect(sessionState) {
            when (sessionState) {
                is SessionState.Ended -> backStack.replaceRoot(SessionEndedRoute)
                else -> {}
            }
        }

        val syncHealth: SyncHealth? = when (sessionState) {
            is SessionState.Connected, is SessionState.Playing -> SyncHealth.GOOD
            is SessionState.Error -> SyncHealth.POOR
            else -> null
        }

        // BE §10.1/§17.9 — a silently dead host must never leave the guest staring at a
        // stale "in sync" screen; HOST_UNREACHABLE renders an explicit recovery state.
        val hostUnreachable =
            (sessionState as? SessionState.Error)?.reason == SessionError.HOST_UNREACHABLE

        // BE §6:356 — guest-local volume: bind the slider to SessionCoordinator's
        // guestVolumeState (session AudioTrack gain only; never STREAM_MUSIC).
        val coordinator = sessionHandle as? SessionCoordinator
        val guestVolume by (coordinator?.guestVolumeState?.volume?.collectAsState()
            ?: remember { mutableStateOf(1f) })

        GuestSessionScreen(
            syncHealth = syncHealth,
            hostUnreachable = hostUnreachable,
            hostDisplayName = sessionHandle?.hostDisplayName,
            sessionCode = sessionHandle?.sessionCode,
            guestCount = sessionHandle?.hostGuestCount?.collectAsState()?.value ?: 0,
            volume = guestVolume,
            onVolumeChange = { newValue ->
                coordinator?.guestVolumeState?.set(
                    newValue,
                    coordinator.presentationScheduler?.getAudioTrack(),
                )
            },
            onLeaveSession = {
                sessionHandle?.leaveSession()
                backStack.replaceRoot(HomeRoute)
            },
        )
    }
    entry<InSessionHostRoute>(metadata = navigationMetadata(reduceMotion)) {
        // C11 — Host in-session UI (FE §9.6) with side sheet panel
        val context = LocalContext.current
        val sessionHandle = SessionHolder.active
        val sessionState by (sessionHandle?.sessionState?.collectAsState()
            ?: remember { mutableStateOf<SessionState?>(null) })
        val guestCount by (sessionHandle?.hostGuestCount?.collectAsState()
            ?: remember { mutableStateOf(0) })

        // BE §8 — live guest list from SessionCoordinator (names, drift, SyncHealth)
        val guests by (sessionHandle?.hostGuests?.collectAsState() ?: remember { mutableStateOf(emptyList<GuestInfo>()) })

        // Session started timestamp (approximate: first guest connection time or session creation)
        val sessionStartedAtMs = guests.minOfOrNull { it.connectedAtMs }
            ?: if (sessionState is SessionState.Advertising) System.currentTimeMillis()
            else 0L

        InSessionHostScreen(
            sessionCode = sessionHandle?.sessionCode,
            guests = guests,
            sessionStartedAtMs = sessionStartedAtMs,
            qrPayload = sessionHandle?.qrPayload,
            onOpenPlayer = { /* PlayerActivity opens independently */ },
            onEndSession = {
                sessionHandle?.endSession()
                backStack.replaceRoot(HomeRoute)
            },
            onLeaveScreen = { backStack.replaceRoot(HomeRoute) },
        )
    }
    entry<SessionEndedRoute>(metadata = navigationMetadata(reduceMotion)) {
        SessionEndedScreen(
            onBackToHome = { backStack.replaceRoot(HomeRoute) },
        )
    }
    entry<PermissionRequiredRoute>(metadata = navigationMetadata(reduceMotion)) { route ->
        val permission = try {
            HearYetPermission.valueOf(route.permission)
        } catch (_: IllegalArgumentException) {
            HearYetPermission.CAMERA
        }
        val context = LocalContext.current
        // BE §1 — the permission-required screen owns the runtime request so "Grant
        // access" actually prompts the system (contextual, never at app launch).
        val requiredPermissions = remember(permission) {
            when (permission) {
                HearYetPermission.CAMERA -> arrayOf(android.Manifest.permission.CAMERA)
                HearYetPermission.NEARBY -> nearbyRuntimePermissions()
                HearYetPermission.STORAGE -> storageRuntimePermissions()
            }
        }
        var deniedOnce by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            if (grants.values.all { it }) {
                backStack.removeLastIfNotRoot()
            } else {
                deniedOnce = true
            }
        }
        PermissionRequiredScreen(
            permission = permission,
            onGrantAccess = {
                if (deniedOnce) {
                    // Permanently denied — deep-link to app settings (FE §9.10).
                    openAppSettings(context)
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            },
            onGoBack = { backStack.removeLastIfNotRoot() },
        )
    }
}

private fun navigationMetadata(reduceMotion: Boolean) = metadata {
    // FE §4.5 — shared-axis fade + slight vertical slide, ~300ms, standard easing
    val defaultSpec = tween<Float>(durationMillis = 300)
    val slideSpec = tween<IntOffset>(durationMillis = 300)
    put(NavDisplay.TransitionKey) {
        if (reduceMotion) {
            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
        } else {
            fadeIn(defaultSpec) + slideInVertically(slideSpec) { it / 8 } togetherWith
                fadeOut(defaultSpec) + slideOutVertically(slideSpec) { -it / 8 }
        }
    }
    put(NavDisplay.PopTransitionKey) {
        if (reduceMotion) {
            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
        } else {
            fadeIn(defaultSpec) + slideInVertically(slideSpec) { -it / 8 } togetherWith
                fadeOut(defaultSpec) + slideOutVertically(slideSpec) { it / 8 }
        }
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        if (reduceMotion) {
            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
        } else {
            fadeIn(defaultSpec) + slideInVertically(slideSpec) { -it / 8 } togetherWith
                fadeOut(defaultSpec) + slideOutVertically(slideSpec) { it / 8 }
        }
    }
}

@Composable
private fun HearYetPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * BE §1 — media-library read permission for the Watch flow, version-appropriate.
 */
private fun storageRuntimePermissions(): Array<String> =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** FE §9.10 — deep-link to the app's system Settings page when a permission is permanently denied. */
private fun openAppSettings(context: android.content.Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

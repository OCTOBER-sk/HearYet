package com.hearyet.app.sync

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.hearyet.app.bluetooth.BluetoothRouteManager
import com.hearyet.app.bluetooth.CodecEstimate
import com.hearyet.app.core.model.ActivityKind
import com.hearyet.app.core.model.RecentActivityEntry
import com.hearyet.app.core.model.SessionError
import com.hearyet.app.core.model.SessionHandle
import com.hearyet.app.core.model.SessionHolder
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.model.GuestInfo
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.feature.permission.nearbyRuntimePermissions
import com.hearyet.app.transport.ControlMessage
import com.hearyet.app.transport.NearbyTransportManager
import com.hearyet.app.transport.SessionPayload
import com.hearyet.app.transport.SessionPayloadCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// flow.first import removed — tryRestoreSession uses synchronous SessionDataStore reads (BE §10.2)
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central orchestrator for a HearYet session — Host or Guest role.
 *
 * BE §4 — ties together [NearbyTransportManager], [ClockSyncManager],
 * [PresentationScheduler], and [DriftCorrectionManager] into a single
 * lifecycle that the UI observes via [sessionState].
 *
 * ## Join-path convergence (BE §4)
 * Both [onQrScanned] (scanned QR) and [onCodeEntered] (typed 6-char code)
 * resolve to the identical discovery path — neither is a separate implementation.
 */
class SessionCoordinator(
    private val context: Context,
    transportOverride: NearbyTransportManager? = null,
    private val guestGreetingManagerFactory: (Context) -> GuestGreetingManager = { GuestGreetingManager(it) },
) : SessionHandle {
    companion object {
        private const val TAG = "SessionCoordinator"

        // C10.4 — Heartbeat (BE §10.1)
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val HOST_UNREACHABLE_TIMEOUT_MS = 15_000L
        private const val HOST_REACHABILITY_CHECK_MS = 1_000L // check every 1s per §17.9

        // Discovery timeout per §3 trigger map (DISCOVERY_FAILED)
        private const val DISCOVERY_TIMEOUT_MS = 15_000L
    }

    // ── Scope ─────────────────────────────────────────────────────────

    @Suppress("unused")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The initial clock-sync batch job (BE §5). Cancelled on teardown so a
     * blocking batch that outlives leaveSession/endSession can never fire its
     * result callback into a torn-down session — the callback's guard checks
     * [Job.isActive] and skips the state update otherwise (BE §10 — leaving
     * must not resurrect a late SYNC_TIMEOUT error state).
     */
    private var syncPipelineJob: Job? = null

    // C10.4 — Heartbeat (BE §10.1)
    private var heartbeatJob: Job? = null
    // Guest-side: last time any message was received from host (nanoTime)
    @Volatile
    private var lastHostMessageNanos: Long = 0L

    // Guest greeting chime — anti-spam flag (directive §2.2 / §4)
    @Volatile
    private var hasReachedPlayingThisSession = false

    /** Last positionMs from a PlaybackState received while the scheduler was still starting. */
    @Volatile
    private var lastKnownPlaybackPositionMs: Long = 0L

    // C10.6 — Persisted state for rejoin (BE §10.2)
    @Volatile
    private var previousEndpointId: String? = null

    /**
     * BE §14.3 — session-stable guest identity for the greeting chime: the persisted
     * rejoin identity on a restore rejoin, otherwise the first connection ID of the
     * session. Never reassigned mid-session, so a RejoinRequest reconnect reuses the
     * same identity string and is never re-greeted (§14.4.2 / §17.13).
     */
    private var greetIdentity: String? = null

    /**
     * BE §4/§10.2 — staged by [performRestoredGuestRejoin] before discovery starts and
     * consumed by the connection-result handler in [startGuestDiscovery], which sends
     * [ControlMessage.RejoinRequest] immediately after the fresh connection succeeds so
     * the Host replaces the old endpoint entry in place instead of appending a duplicate.
     */
    @Volatile
    private var pendingRejoinRequest: Pair<String, String>? = null // (previousEndpointId, displayName)

    /** BE §4 — true while waiting for the host to confirm the sessionId handshake. */
    @Volatile
    private var awaitingSessionHandshake: Boolean = false

    // ── Transport ─────────────────────────────────────────────────────

    /** Injectable for tests; production call sites use the default. */
    val transport: NearbyTransportManager = transportOverride ?: NearbyTransportManager(context)

    /**
     * BE §3/§17.7 — Nearby Connections is delivered by Google Play services.
     * Returns a user-facing detail string when Play services is missing or
     * disabled on this device, or null when the API is available.
     *
     * This turns the blanket "device doesn't support the connection" into an
     * actionable message on devices without a functional Play-services stack
     * (custom ROMs, de-Googled builds) before we ever touch the Nearby API.
     */
    private fun nearbyApiProblemDetail(): String? {
        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(context)
        if (status == ConnectionResult.SUCCESS) return null
        val label = availability.getErrorString(status)
        Log.w(TAG, "Google Play services unavailable (status=$status: $label)")
        return "Google Play services is not available on this device ($label). HearYet's nearby connection uses it."
    }

    // ── Host audio fan-out (BE §6 backpressure) ────────────────────────────

    /** Per-guest bounded outbound audio queues, populated by the PCM tap. */
    private val guestQueues = java.util.concurrent.ConcurrentHashMap<String, GuestOutboundQueue>()

    /** Per-guest sender threads that dequeue and deliver via STREAM. */
    private val guestSenderThreads = java.util.concurrent.ConcurrentHashMap<String, Thread>()

    // ── Bluetooth (Guest-side) — BE §9 ────────────────────────────────────

    internal var bluetoothRouteManager: BluetoothRouteManager? = null

    // ── Sync pipeline (Guest-side) — BE §5, §7, §8 ──────────────────

    /** Created when guest connects; runs Cristian's-algorithm clock sync. */
    internal var clockSyncManager: ClockSyncManager? = null

    /** Created after clock sync converges; feeds AudioTrack from ring buffer. */
    internal var presentationScheduler: PresentationScheduler? = null

    /** Created after scheduler starts; monitors drift and applies speed nudges. */
    internal var driftCorrectionManager: DriftCorrectionManager? = null

    /** Guest greeting chime — plays once per guest per session on first Playing (directive §2). */
    internal var guestGreetingManager: GuestGreetingManager? = null

    /** BE §6 — guest audio focus handler (permanent loss → pause; transient → duck). */
    internal var guestAudioFocusManager: GuestAudioFocusManager? = null

    /** BE §6:356 — single source of truth for the Guest's local playback volume. */
    val guestVolumeState = GuestVolumeState()

    // ── Session identity ──────────────────────────────────────────────

    private var sessionId: String? = null
    private var hostEndpointName: String? = null
    private var displayName: String = android.os.Build.MODEL ?: "Android"

    /** FE §9.13 — the Host's display name, known to a Guest from the QR payload. */
    private var hostDisplayNameValue: String? = null

    override val hostDisplayName: String?
        get() = hostDisplayNameValue

    /**
     * Guest-side: the endpoint ID of the connected host.
     * Tracked locally because transport.hostEndpointId is only set
     * internally by the connection lifecycle callback and we need
     * access to it before the transport's internal state settles.
     */
    @Volatile
    var connectedHostEndpointId: String? = null
        private set

    /** The encoded QR payload string, generated once at session creation. */
    @Volatile
    override var qrPayload: String? = null
        private set

    /** The 6-character session code for manual entry (Host side). */
    @Volatile
    override var sessionCode: String? = null
        private set

    // ── Observable state ──────────────────────────────────────────────

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _hostGuestCount = MutableStateFlow(0)
    override val hostGuestCount: StateFlow<Int> = _hostGuestCount.asStateFlow()

    private val _hostGuests = MutableStateFlow<List<GuestInfo>>(emptyList())
    override val hostGuests: StateFlow<List<GuestInfo>> = _hostGuests.asStateFlow()

    // ── Role ──────────────────────────────────────────────────────────

    @Volatile
    var role: SessionRole = SessionRole.Guest
        private set

    /** C11 — SessionHandle.isHost for cross-module access. */
    override val isHost: Boolean get() = role is SessionRole.Host

    // BE §10.2 — DataStore-backed persistence (replaces SharedPreferences)
    private val sessionPrefs = SessionDataStore(context)

    // ── Callbacks for UI wiring ───────────────────────────────────────

    /** Guest: fired when connection to host succeeds and clock sync should begin. */
    var onConnectedToHost: ((hostEndpointId: String) -> Unit)? = null

    /**
     * FE Addendum §16 — Recent Activity write sink, wired by the UI layer.
     * Receives SESSION_HOSTED / SESSION_JOINED entries at session lifecycle points.
     */
    var recentActivitySink: ((RecentActivityEntry) -> Unit)? = null

    private var hasRecordedJoinedThisSession = false

    // ═══════════════════════════════════════════════════════════════════
    // Host flow
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Start a new session as Host.
     *
     * Generates a session identity, builds the QR payload, and begins
     * advertising so guests can join.  Media picking happens later —
     * the QR is available immediately (BE §4, FE §9.4).
     */
    fun startAsHost(hostDisplayName: String) {
        role = SessionRole.Host
        displayName = hostDisplayName

        // BE §3/§17.7 — fail honestly before generating a QR that can never
        // be joined: no Play services means Nearby Connections cannot start.
        nearbyApiProblemDetail()?.let { problem ->
            Log.w(TAG, "Host: $problem")
            _sessionState.value = SessionState.Error(SessionError.DEVICE_INCOMPATIBLE, detail = problem)
            return
        }

        val code = SessionPayloadCodec.generateSessionCode()
        val id = java.util.UUID.randomUUID().toString()
        val endpointName = SessionPayload.buildEndpointName(code)

        sessionId = id
        sessionCode = code
        hostEndpointName = endpointName

        val payload = SessionPayload(
            sessionId = id,
            sessionCode = code,
            hostEndpointName = endpointName,
            hostDisplayName = hostDisplayName,
        )
        qrPayload = SessionPayloadCodec.encode(payload)

        // FE Addendum §16 — SESSION_HOSTED write at session start.
        recentActivitySink?.invoke(
            RecentActivityEntry(
                id = java.util.UUID.randomUUID().toString(),
                kind = ActivityKind.SESSION_HOSTED,
                title = "Session",
                timestampMs = System.currentTimeMillis(),
            ),
        )

        // Wire transport callbacks for Host role
        transport.onEndpointConnected = { endpointId, remoteName ->
            Log.d(TAG, "Host: guest connected — $endpointId ($remoteName)")
            _hostGuestCount.update { transport.connectedEndpointCount }

            // BE §8 — add guest to live guest list for the Host UI
            _hostGuests.update { current ->
                if (current.any { it.endpointId == endpointId }) current
                else current + GuestInfo(
                    endpointId = endpointId,
                    displayName = remoteName,
                    clockOffsetMs = 0.0,
                    lastRttMs = 0L,
                    driftMs = 0.0,
                    syncHealth = SyncHealth.GOOD,
                    connectedAtMs = System.currentTimeMillis(),
                )
            }

            // BE §4 — When a guest connects while host is still Advertising
            // (media not yet picked), transition to WaitingForMedia so the
            // Create-sheet label shows "Waiting for guests…" correctly.
            val currentState = _sessionState.value
            if (currentState is SessionState.Advertising) {
                _sessionState.value = SessionState.WaitingForMedia
            }

            // BE §6 backpressure — create per-guest outbound queue + sender thread
            startGuestAudioSender(endpointId)

            // C10.3 — Latecomer seeding: if host is already playing, send
            // PlaybackState immediately to seed the new guest's scheduler (BE §4, §7)
            if (currentState is SessionState.Playing) {
                Log.d(TAG, "Host: latecomer join — sending PlaybackState to $endpointId")
                transport.sendControlMessage(
                    endpointId,
                    ControlMessage.PlaybackState(
                        isPlaying = true,
                        positionMs = currentState.positionMs,
                        sharedClockTimestampNanos = System.nanoTime(),
                    ),
                )
            }
        }
        transport.onEndpointDisconnected = { endpointId ->
            _hostGuestCount.update { transport.connectedEndpointCount }
            // BE §10 — remove disconnected guest from live guest list
            _hostGuests.update { current -> current.filter { it.endpointId != endpointId } }
            stopGuestAudioSender(endpointId)
        }
        transport.onControlMessage = { endpointId, message ->
            handleHostControlMessage(endpointId, message)
        }
        // BE §3/§17.7 — DEVICE_INCOMPATIBLE is reachable: Nearby cannot start on
        // this device (no Play Services / unsupported), never a silent failure.
        // detail preserves the underlying status text instead of hiding it.
        transport.onError = {
            Log.w(TAG, "Host: transport error — $it")
            _sessionState.value = SessionState.Error(SessionError.DEVICE_INCOMPATIBLE, detail = it)
        }

        // BE §5 — Host creates ClockSyncManager so handleSyncRequest can respond
        // to guest clock-sync probes. The host never initiates sync batches — it
        // only responds to incoming ClockSyncRequest messages.
        clockSyncManager = ClockSyncManager(transport)

        transport.startAdvertising(endpointName)
        _sessionState.value = SessionState.Advertising

        // C11 — Register with SessionHolder so PlayerActivity can observe
        SessionHolder.active = this

        // C10.6 — Persist session identity for rejoin across restart (BE §10.2)
        saveSessionState()

        // C10.4 — Start heartbeat to keep guests aware of host liveness (BE §10.1)
        startHeartbeat()

        Log.d(TAG, "Host session started: $endpointName")
    }

    /** End the session as Host — broadcast SessionEnded and tear down. */
    override fun endSession() {
        if (role != SessionRole.Host) return
        transport.broadcastControlMessage(ControlMessage.SessionEnded)
        stopHeartbeat()
        clearSessionState()
        teardown()
        _sessionState.value = SessionState.Ended
    }

    /** BE §7 — Host seek: broadcast SeekTo to all connected guests and update local state. */
    override fun onHostSeeked(positionMs: Long) {
        if (role != SessionRole.Host) return
        transport.broadcastControlMessage(
            ControlMessage.SeekTo(positionMs, System.nanoTime())
        )
        // Update host's own observable state so UI stays in sync (BE §7)
        _sessionState.value = SessionState.Playing(positionMs)
    }

    /** BE §7 — Host pause/resume: broadcast PlaybackState (treat unpause as seek per BE §7). */
    override fun onHostPlayPause(isPlaying: Boolean, positionMs: Long) {
        if (role != SessionRole.Host) return
        broadcastPlaybackState(isPlaying, positionMs)
        // Update host's own observable state so the In-Session UI reflects current position
        if (isPlaying) {
            _sessionState.value = SessionState.Playing(positionMs)
        }
    }

    /** BE §4/§7 — broadcast the current playback state to every connected guest. */
    private fun broadcastPlaybackState(isPlaying: Boolean, positionMs: Long) {
        transport.broadcastControlMessage(
            ControlMessage.PlaybackState(isPlaying, positionMs, System.nanoTime())
        )
    }

    /** BE §7 — Host media change: broadcast MediaChanged then PlaybackState and update local state. */
    override fun onHostMediaChanged(mediaTitle: String) {
        if (role != SessionRole.Host) return
        val nowNanos = System.nanoTime()
        transport.broadcastControlMessage(
            ControlMessage.MediaChanged(mediaTitle, nowNanos)
        )
        transport.broadcastControlMessage(
            ControlMessage.PlaybackState(isPlaying = true, positionMs = 0L, nowNanos)
        )
        // Reset position to 0 for the new media
        _sessionState.value = SessionState.Playing(positionMs = 0L)
    }

    /** BE §7/§12 — broadcast AudioTrackChanged so guests flush-and-reseed like a seek. */
    override fun onHostAudioTrackChanged(trackId: String) {
        if (role != SessionRole.Host) return
        transport.broadcastControlMessage(
            ControlMessage.AudioTrackChanged(
                trackId = trackId,
                sharedClockTimestampNanos = System.nanoTime(),
            )
        )
    }

    private fun handleHostControlMessage(endpointId: String, message: ControlMessage) {
        when (message) {
            is ControlMessage.GuestJoined -> {
                Log.d(TAG, "Host: GuestJoined from ${message.displayName}")
                _hostGuestCount.update { transport.connectedEndpointCount }
                // BE §8 — update guest display name if we had a placeholder
                _hostGuests.update { current ->
                    current.map { guest ->
                        if (guest.endpointId == endpointId)
                            guest.copy(displayName = message.displayName)
                        else guest
                    }
                }
            }
            is ControlMessage.DriftReport -> {
                Log.v(TAG, "Host: DriftReport from $endpointId: ${"%.1f".format(message.driftMs)}ms")
                // BE §8 — update live guest list with drift and health
                _hostGuests.update { current ->
                    current.map { guest ->
                        if (guest.endpointId == endpointId) {
                            val health = when {
                                kotlin.math.abs(message.driftMs) < 15.0 -> SyncHealth.GOOD
                                kotlin.math.abs(message.driftMs) < 50.0 -> SyncHealth.DEGRADED
                                else -> SyncHealth.POOR
                            }
                            guest.copy(driftMs = message.driftMs, syncHealth = health)
                        } else guest
                    }
                }
            }
            is ControlMessage.RejoinRequest -> {
                // C10.5 — BE §4 / §10.2: look up previousEndpointId in guest list,
                // replace in place (preserve displayName, reset sync stats).
                Log.d(TAG, "Host: RejoinRequest from $endpointId (prev=${message.previousEndpointId})")
                _hostGuestCount.update { transport.connectedEndpointCount }
                _hostGuests.update { current ->
                    val prevIdx = current.indexOfFirst { it.endpointId == message.previousEndpointId }
                    if (prevIdx >= 0) {
                        current.toMutableList().apply {
                            // Replace old endpointId entry with fresh one, preserving name
                            this[prevIdx] = this[prevIdx].copy(
                                endpointId = endpointId,
                                clockOffsetMs = 0.0,
                                lastRttMs = 0L,
                                driftMs = 0.0,
                                syncHealth = SyncHealth.GOOD,
                                connectedAtMs = System.currentTimeMillis(),
                            )
                        }
                    } else {
                        // Not found — treat as new guest
                        current + GuestInfo(
                            endpointId = endpointId,
                            displayName = message.displayName,
                            clockOffsetMs = 0.0,
                            lastRttMs = 0L,
                            driftMs = 0.0,
                            syncHealth = SyncHealth.GOOD,
                            connectedAtMs = System.currentTimeMillis(),
                        )
                    }
                }
                val currentState = _sessionState.value
                if (currentState is SessionState.Playing) {
                    Log.d(TAG, "Host: re-seeding rejoined guest $endpointId at ${currentState.positionMs}ms")
                    transport.sendControlMessage(
                        endpointId,
                        ControlMessage.PlaybackState(
                            isPlaying = true,
                            positionMs = currentState.positionMs,
                            sharedClockTimestampNanos = System.nanoTime(),
                        ),
                    )
                }
            }
            is ControlMessage.ClockSyncRequest -> {
                // BE §5 — Host responds to guest clock-sync probes.
                clockSyncManager?.handleSyncRequest(endpointId, message)
            }
            is ControlMessage.SessionHandshake -> {
                // BE §4 — confirm the host's sessionId so a name-collision guest can be rejected.
                Log.d(TAG, "Host: SessionHandshake from $endpointId")
                transport.sendControlMessage(
                    endpointId,
                    ControlMessage.SessionHandshakeAck(sessionId ?: ""),
                )
            }
            else -> { /* other messages handled by specialized managers */ }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Guest join flow — BE §4
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Guest: a QR was successfully scanned.  Decodes the payload,
     * validates it, and starts discovery for the host endpoint.
     *
     * BE §4 — guest join sequence.
     */
    fun onQrScanned(rawPayload: String) {
        if (role != SessionRole.Guest) return
        if (_sessionState.value !is SessionState.Idle) return
        if (!hasNearbyPermissions()) {
            _sessionState.value = SessionState.Error(SessionError.PERMISSION_MISSING)
            return
        }

        val payload = SessionPayloadCodec.decode(rawPayload)
        if (payload == null) {
            _sessionState.value = SessionState.Error(SessionError.QR_INVALID)
            return
        }

        if (payload.protocolVersion != SessionPayload.CURRENT_PROTOCOL_VERSION) {
            _sessionState.value = SessionState.Error(SessionError.PAYLOAD_INVALID)
            return
        }

        sessionId = payload.sessionId
        sessionCode = payload.sessionCode
        hostEndpointName = payload.hostEndpointName
        hostDisplayNameValue = payload.hostDisplayName

        // Fresh join (not a restore): make sure no stale staged RejoinRequest from a
        // previously failed restore-rejoin survives into this connection.
        pendingRejoinRequest = null

        startGuestDiscovery(payload.hostEndpointName)
    }

    /**
     * Guest: a 6-character session code was typed manually.
     * Resolves to the identical discovery path as a scanned QR (BE §4).
     *
     * The code maps to the host endpoint name via [SessionPayload.buildEndpointName]
     * — the same function the Host used when it started advertising — so a typed
     * code and a scanned QR converge on the same discovery path.
     */
    fun onCodeEntered(code: String) {
        if (role != SessionRole.Guest) return
        if (_sessionState.value !is SessionState.Idle) return
        if (!hasNearbyPermissions()) {
            _sessionState.value = SessionState.Error(SessionError.PERMISSION_MISSING)
            return
        }

        val normalized = code.uppercase().trim().take(6)
        if (normalized.length < 6) {
            _sessionState.value = SessionState.Error(SessionError.QR_INVALID)
            return
        }

        sessionCode = normalized
        hostEndpointName = SessionPayload.buildEndpointName(normalized)

        // Fresh join (not a restore) — clear any stale staged RejoinRequest (see onQrScanned).
        pendingRejoinRequest = null

        startGuestDiscovery(hostEndpointName!!)
    }

    /** BE §1/§17.7 — all Nearby/BT runtime permissions currently granted? */
    private fun hasNearbyPermissions(): Boolean {
        return nearbyRuntimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Common discovery path shared by both [onQrScanned] and [onCodeEntered].
     *
     * BE §4 — the two entry points converge here: discovery filters for the
     * host endpoint name, connects on match, then proceeds through the
     * identical Discovering → ClockSyncing → Connected flow.
     */
    private fun startGuestDiscovery(expectedEndpointName: String) {
        // BE §3/§17.7 — fail honestly before discovery: no Play services means
        // Nearby Connections cannot scan for the host.
        nearbyApiProblemDetail()?.let { problem ->
            Log.w(TAG, "Guest: $problem")
            _sessionState.value = SessionState.Error(SessionError.DEVICE_INCOMPATIBLE, detail = problem)
            return
        }

        _sessionState.value = SessionState.Discovering

        // Discovery timeout — transition to DISCOVERY_FAILED if no host found within 15s
        scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (_sessionState.value is SessionState.Discovering) {
                Log.w(TAG, "Guest: discovery timed out after ${DISCOVERY_TIMEOUT_MS}ms")
                transport.stopDiscovery()
                _sessionState.value = SessionState.Error(SessionError.DISCOVERY_FAILED)
            }
        }

        // Guest greeting chime — allocate & preload when guest role is active (directive §2.3)
        guestGreetingManager = guestGreetingManagerFactory(context)
        if (pendingRejoinRequest != null) {
            // BE §14.4.2/§17.13 — a restore rejoin must not be re-greeted: seed the
            // in-memory greeted set with the identity persisted when the chime first
            // played this session (SessionDataStore survives process death).
            guestGreetingManager?.seedGreetedIdentity(
                sessionPrefs.getString(SessionDataStore.KEY_GREETED_IDENTITY)
            )
        }
        guestGreetingManager?.preload()

        // Wire the host-discovered callback for filtering
        transport.onHostDiscovered = { endpointId, discoveredName ->
            if (SessionPayload.isMatchingEndpoint(discoveredName, expectedEndpointName)) {
                Log.d(TAG, "Guest: matching host found — $discoveredName")
                transport.stopDiscovery()
                connectToHost(endpointId)
            }
        }

        transport.onConnectionResult = { success ->
            if (success) {
                Log.d(TAG, "Guest: connected to host — ${connectedHostEndpointId}")
                // C10.4 — Reset heartbeat timer on connection
                lastHostMessageNanos = System.nanoTime()
                _sessionState.value = SessionState.ClockSyncing

                // BE §4/§10.2 — restored-guest rejoin: send RejoinRequest so the Host
                // replaces the previous endpoint entry in place (dedupe) rather than
                // appending a fresh guest. Consume it here — a later disconnect/reconnect
                // within this process is a normal join, not a restore rejoin.
                pendingRejoinRequest?.let { (prevId, name) ->
                    pendingRejoinRequest = null
                    val hostId = connectedHostEndpointId ?: ""
                    Log.d(TAG, "Guest rejoin: sending RejoinRequest to $hostId (prev=$prevId)")
                    transport.sendControlMessage(
                        hostId,
                        ControlMessage.RejoinRequest(previousEndpointId = prevId, displayName = name),
                    )
                }

                // C10.6 — Persist session identity for rejoin across restart (BE §10.2)
                previousEndpointId = connectedHostEndpointId
                saveSessionState()

                // BE §14.3 — the first connection ID of the session becomes its greet
                // identity (the restore-rejoin path already staged the persisted prevId
                // in performRestoredGuestRejoin — never overwrite it here).
                greetIdentity = greetIdentity ?: connectedHostEndpointId

                // BE §3 — announce this guest to the Host so its guest list shows the
                // entered display name. The Host keys guest entries by the connection
                // endpointId (which the guest cannot know about itself), so the payload's
                // endpointId is informational only — the displayName is what matters.
                transport.sendControlMessage(
                    connectedHostEndpointId ?: "",
                    ControlMessage.GuestJoined(endpointId = "", displayName = displayName),
                )

                // Notify UI layer
                val hostId = connectedHostEndpointId ?: ""
                onConnectedToHost?.invoke(hostId)

                // BE §10.1 — Start guest-side heartbeat checker so HOST_UNREACHABLE
                // detection runs every 1s (checks lastHostMessageNanos > 15s gap).
                startHeartbeat()

                // BE §4 — post-connect handshake confirms sessionId before the clock
                // sync starts, so a name collision between two nearby hosts can't
                // misroute a guest. The QR path knows the expected sessionId; the
                // typed-code path (no sessionId) proceeds directly after the code
                // already resolved to the exact advertised endpoint name.
                val expectedSessionId = sessionId
                if (expectedSessionId != null) {
                    awaitingSessionHandshake = true
                    transport.sendControlMessage(hostId, ControlMessage.SessionHandshake(expectedSessionId))
                } else {
                    startGuestSyncPipeline(hostId)
                }
            } else {
                Log.w(TAG, "Guest: connection failed")
                // Clean any stale half-open connection so the next Join retry can't
                // hit Nearby status 8003 (ALREADY_CONNECTED_TO_ENDPOINT).
                connectedHostEndpointId?.let { transport.disconnect(it) }
                connectedHostEndpointId = null
                _sessionState.value = SessionState.Error(SessionError.CONNECTION_FAILED)
            }
        }

        transport.onControlMessage = { endpointId, message ->
            handleGuestControlMessage(endpointId, message)
        }

        // BE §3/§17.7 — DEVICE_INCOMPATIBLE is reachable when Nearby cannot start
        // discovery on this device (never a silent failure). detail preserves the
        // underlying status text (e.g. Bluetooth/Wi-Fi off, Nearby disabled).
        transport.onError = {
            Log.w(TAG, "Guest: transport error — $it")
            if (_sessionState.value is SessionState.Discovering ||
                _sessionState.value is SessionState.ClockSyncing
            ) {
                _sessionState.value = SessionState.Error(SessionError.DEVICE_INCOMPATIBLE, detail = it)
            }
        }

        transport.startDiscovery()
    }

    private fun connectToHost(endpointId: String) {
        // If a previous attempt already connected to this endpoint (e.g. the user
        // backed out of the Join sheet mid-connect), Nearby refuses a second
        // requestConnection with status 8003 (ALREADY_CONNECTED_TO_ENDPOINT).
        // Tear the stale connection down first so the retry starts clean.
        connectedHostEndpointId?.let { transport.disconnect(it) }
        connectedHostEndpointId = endpointId
        transport.requestConnection(endpointId, displayName)
    }

    /**
     * BE §5 + §7 + §8 — guest sync pipeline, triggered automatically after
     * a successful connection to the host.
     *
     * Runs ClockSyncManager.performSyncBatch() on a background thread (it blocks
     * with Thread.sleep), then on convergence: creates PresentationScheduler,
     * opens AudioTrack, wires transport chunks, starts DriftCorrectionManager,
     * and begins background re-sync.
     *
     * On sync failure: transitions to SessionState.Error(SYNC_TIMEOUT).
     */
    private fun startGuestSyncPipeline(hostEndpointId: String) {
        clockSyncManager = ClockSyncManager(transport)
        // BE §10.1 — ClockSyncResponse messages are intercepted by the batch listener,
        // so reset the HOST_UNREACHABLE timer whenever the host responds during sync.
        clockSyncManager?.onHostActivity = { lastHostMessageNanos = System.nanoTime() }

        syncPipelineJob = scope.launch(Dispatchers.IO) {
            clockSyncManager?.performSyncBatch(hostEndpointId) { offsetNanos, stddevMs ->
                // Teardown guard (BE §10): the batch is blocking, so a cancel
                // cannot stop it mid-flight — but its callbacks must never
                // update a session the user already left/ended.
                if (syncPipelineJob?.isActive != true) return@performSyncBatch
                if (offsetNanos >= 0) {
                    Log.d(TAG, "Guest sync converged: offset=${"%.3f".format(offsetNanos / 1_000_000.0)}ms stddev=${"%.3f".format(stddevMs)}ms")
                    scope.launch(Dispatchers.Main) {
                        onGuestSyncReady(offsetNanos, hostEndpointId)
                    }
                } else {
                    Log.w(TAG, "Guest sync failed to converge: stddev=${"%.3f".format(stddevMs)}ms")
                    scope.launch(Dispatchers.Main) {
                        // Clean up the half-started connection so a retry starts fresh —
                        // leaving it connected makes the next Join hit Nearby status 8003
                        // (ALREADY_CONNECTED_TO_ENDPOINT) and "Try again" can never succeed.
                        connectedHostEndpointId?.let { transport.disconnect(it) }
                        connectedHostEndpointId = null
                        transport.onAudioChunkReceived = null
                        _sessionState.value = SessionState.Error(SessionError.SYNC_TIMEOUT)
                    }
                }
            }
        }
    }

    /**
     * BE §7 / §8 — clock sync converged.  Spin up the presentation scheduler,
     * wire transport → scheduler, start drift correction, and begin background re-sync.
     */
    private fun onGuestSyncReady(offsetNanos: Double, hostEndpointId: String) {
        presentationScheduler = PresentationScheduler()
        presentationScheduler?.clockOffsetNanos = offsetNanos

        if (presentationScheduler?.openAudioTrack() != true) {
            Log.e(TAG, "Guest: failed to open AudioTrack")
            _sessionState.value = SessionState.Error(SessionError.DEVICE_INCOMPATIBLE)
            return
        }

        // BE §6:356 — apply the guest's chosen local volume to the session AudioTrack
        // (guest-local gain only; never touches the system STREAM_MUSIC volume).
        presentationScheduler?.getAudioTrack()?.setVolume(guestVolumeState.current)

        presentationScheduler?.start()

        // BE §7 — exiting ClockSyncing reuses the single seeding code path
        // (identical to latecomer-join / rejoin / seek seeding): the scheduler
        // starts from "now", never from session start or a stale position.
        presentationScheduler?.seedFromNow()

        // BE §6 — guest audio focus: permanent loss → PAUSE the AudioTrack (never
        // release it — releasing would make focus regain unable to resume), then on
        // regain flush-and-re-seed from "now" like a fresh PlaybackState.
        guestAudioFocusManager = GuestAudioFocusManager(context).apply {
            onPermanentFocusLost = {
                Log.d(TAG, "Guest: permanent focus lost — pausing AudioTrack")
                presentationScheduler?.getAudioTrack()?.pause()
            }
            onFocusRegained = {
                Log.d(TAG, "Guest: focus regained — flush + re-seed")
                // BE §6:356 — restore the user's own chosen level, never a hardcoded 1.0f.
                presentationScheduler?.getAudioTrack()?.setVolume(guestVolumeState.current)
                presentationScheduler?.flush()
                presentationScheduler?.seedFromNow()
            }
            onTransientFocusDuck = {
                // BE §6:352 — transient loss → duck relative to the user's current level.
                Log.d(TAG, "Guest: transient focus loss — ducking")
                presentationScheduler?.getAudioTrack()?.setVolume(guestVolumeState.current * 0.2f)
            }
            onTransientFocusLost = {
                // BE §6:352 — non-duckable transient loss (short call, voice prompt):
                // duck to near-silent instead of the previous no-op. Keep the clock
                // advancing — pausing would leave the guest behind the schedule on
                // regain, and DriftCorrectionManager (BE §8) absorbs the brief gap.
                Log.d(TAG, "Guest: transient focus lost — ducking to near-silent")
                presentationScheduler?.getAudioTrack()?.setVolume(0.05f)
            }
            requestFocus()
        }

        // Wire incoming audio chunks from transport → scheduler
        transport.onAudioChunkReceived = { chunk ->
            presentationScheduler?.onChunkReceived(chunk)
        }

        // Start drift correction (BE §8 — evaluates every 1-2s, reports every 2s)
        driftCorrectionManager = DriftCorrectionManager(transport)
        // BE §5 — degraded-mode entry marks SyncHealth.POOR from the start
        // (the doc's sanctioned fallback; drift correction normalizes it within
        // a couple of report cycles on a healthy link).
        if (clockSyncManager?.lastSyncDegraded == true) {
            driftCorrectionManager?.markDegradedEntry()
            Log.w(TAG, "Guest: entered with degraded sync — health POOR until drift correction normalizes")
        }
        presentationScheduler?.getAudioTrack()?.let { track ->
            driftCorrectionManager?.start(track, presentationScheduler!!, hostEndpointId)
        }

        // Start Bluetooth route monitoring & codec-aware lookahead (BE §9)
        // Update scheduler lookahead when codec changes (per-guest, independent detection)
        bluetoothRouteManager = BluetoothRouteManager(context) { codec ->
            presentationScheduler?.lookaheadMs = codec.defaultLookaheadMs
            Log.d(TAG, "Guest: codec $codec → lookaheadMs = ${codec.defaultLookaheadMs}")
        }
        bluetoothRouteManager?.start()

        // Start background clock re-sync for crystal drift (BE §5: every 30-60s)
        clockSyncManager?.startBackgroundResync(hostEndpointId)

        // BE §4/§7 — if a PlaybackState (latecomer/rejoin seed or media start) arrived
        // while the clock was still syncing, do not regress the state machine back to
        // Connected: the guest is already Playing and the scheduler is live.
        _sessionState.value = if (hasReachedPlayingThisSession) {
            SessionState.Playing(positionMs = lastKnownPlaybackPositionMs)
        } else {
            SessionState.Connected(guestCount = 0)
        }
        Log.d(TAG, "Guest sync pipeline ready — scheduler running, drift monitoring active")

        // FE Addendum §16 — SESSION_JOINED write once per session on first connect.
        if (!hasRecordedJoinedThisSession) {
            hasRecordedJoinedThisSession = true
            recentActivitySink?.invoke(
                RecentActivityEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    kind = ActivityKind.SESSION_JOINED,
                    title = "Session",
                    timestampMs = System.currentTimeMillis(),
                ),
            )
        }

        // BE §10 — start foreground service so guest audio survives background/Doze
        context.startForegroundService(
            android.content.Intent(context, com.hearyet.app.feature.session.guest.GuestSessionService::class.java)
        )
    }

    private fun handleGuestControlMessage(endpointId: String, message: ControlMessage) {
        // C10.4 — Any message from host resets the unreachable timer (BE §10.1)
        lastHostMessageNanos = System.nanoTime()

        when (message) {
            is ControlMessage.SessionEnded -> {
                Log.d(TAG, "Guest: SessionEnded received")
                stopHeartbeat()
                clearSessionState()
                guestGreetingManager?.onSessionEnded()
                hasReachedPlayingThisSession = false
                _sessionState.value = SessionState.Ended
                teardown()
            }
            is ControlMessage.PlaybackState -> {
                val current = _sessionState.value
                lastKnownPlaybackPositionMs = message.positionMs
                when {
                    // Initial entry: transitioning from Connected or ClockSyncing
                    current is SessionState.Connected ||
                        current is SessionState.ClockSyncing -> {
                        _sessionState.value = SessionState.Playing(message.positionMs)

                        // Guest greeting chime — §2.2: fire on FIRST Playing transition only
                        if (!hasReachedPlayingThisSession) {
                            hasReachedPlayingThisSession = true
                            // BE §14.3 — the session-stable rejoin identity, never the raw
                            // transient endpointId (greetIdentity is captured once per session:
                            // the persisted prevId on a restore rejoin, the first connection ID
                            // on a fresh join). Volume comes from guestVolumeState (BE §14.6.4),
                            // not the system STREAM_MUSIC stream.
                            val identity = greetIdentity ?: connectedHostEndpointId ?: "unknown"
                            guestGreetingManager?.maybeGreet(identity, guestVolumeState.current)
                            // BE §14.4.2/§17.13 — persist the greeted identity so a process
                            // death + RejoinRequest rejoin is never re-greeted (the in-memory
                            // set is gone after restart). Cleared with the rest of the session
                            // state on End/Leave (§14.4.1/§14.4.3).
                            sessionPrefs.putString(SessionDataStore.KEY_GREETED_IDENTITY, identity)
                        }
                    }
                    // C12 prep — mid-session pause/resume (BE §7):
                    // flush the scheduler and re-seed from the new PlaybackState
                    current is SessionState.Playing -> {
                        presentationScheduler?.flush()
                        presentationScheduler?.seedFromNow()
                        _sessionState.value = SessionState.Playing(message.positionMs)
                        Log.d(TAG, "Guest: PlaybackState update — flushed scheduler, positionMs=${message.positionMs}")
                    }
                }
            }
            is ControlMessage.Heartbeat -> {
                // C10.4 — Heartbeat received; timer already reset above
                Log.v(TAG, "Guest: heartbeat received")
            }
            is ControlMessage.SeekTo -> {
                // BE §7 — flush ring buffer entirely, re-seed from new position
                presentationScheduler?.flush()
                presentationScheduler?.seedFromNow()
                _sessionState.value = SessionState.Playing(message.positionMs)
                Log.d(TAG, "Guest: SeekTo — flushed scheduler, positionMs=${message.positionMs}")
            }
            is ControlMessage.MediaChanged -> {
                // BE §7 — flush old media, discard queued chunks; re-seed only once
                // the new PlaybackState arrives (the PlaybackState handler runs the
                // same flush-and-reseed path as a seek).
                presentationScheduler?.flush()
                Log.d(TAG, "Guest: MediaChanged — flushed scheduler, waiting for PlaybackState (${message.mediaTitle})")
            }
            is ControlMessage.AudioTrackChanged -> {
                // BE §7/§12 — a different audio track changes the PCM feed; flush the
                // ring buffer and re-seed once the next PlaybackState arrives, exactly
                // like a seek.
                presentationScheduler?.flush()
                presentationScheduler?.seedFromNow()
                Log.d(TAG, "Guest: AudioTrackChanged — flushed scheduler (${message.trackId})")
            }
            is ControlMessage.SessionHandshakeAck -> {
                // BE §4 — host confirmed its sessionId; if it doesn't match the QR
                // payload's sessionId, reject the connection (name-collision guard).
                if (awaitingSessionHandshake) {
                    awaitingSessionHandshake = false
                    val expected = sessionId
                    if (expected != null && message.sessionId == expected) {
                        Log.d(TAG, "Guest: session handshake confirmed")
                        startGuestSyncPipeline(connectedHostEndpointId ?: "")
                    } else {
                        Log.w(TAG, "Guest: session handshake mismatch")
                        _sessionState.value = SessionState.Error(SessionError.CONNECTION_FAILED)
                        connectedHostEndpointId?.let { transport.disconnect(it) }
                    }
                }
            }
            is ControlMessage.ClockSyncResponse -> {
                // BE §5 — route responses directly into the active sync batch's queue.
                // This replaces the old transport.onControlMessage handler-swap: the
                // coordinator's handler is never replaced; responses are forwarded here
                // while a batch is running, and ignored otherwise (no active sync = no queue).
                clockSyncManager?.pendingSyncResponseQueue?.offer(message)
            }
            else -> { /* other messages handled by specialized managers */ }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // State helpers
    // ═══════════════════════════════════════════════════════════════════

    /** Move from WaitingForMedia to Playing (Host picked media and started).
     *  BE §4 — broadcasts the initial [ControlMessage.PlaybackState] so every
     *  already-connected guest transitions straight from WaitingForMedia to
     *  scheduled playback (no re-join required). */
    fun onMediaStarted(positionMs: Long) {
        if (role != SessionRole.Host) return
        broadcastPlaybackState(isPlaying = true, positionMs = positionMs)
        _sessionState.value = SessionState.Playing(positionMs)
    }

    /** Set the display name for this device (Guest name entry, FE §9.5). */
    fun setDisplayName(name: String) {
        displayName = name
    }

    /** Reset to Idle for retry after an error. */
    fun resetToIdle() {
        // Clear per-attempt flags so a retry starts completely clean:
        // - awaitingSessionHandshake: stale 'true' would swallow the next SessionHandshakeAck
        // - hasReachedPlayingThisSession: prevents stale Playing state on retry
        // - lastKnownPlaybackPositionMs: old position is irrelevant for a fresh connection
        awaitingSessionHandshake = false
        hasReachedPlayingThisSession = false
        lastKnownPlaybackPositionMs = 0L
        // A fresh attempt gets a fresh greet identity (a restore-rejoin retry re-stages
        // the persisted prevId in performRestoredGuestRejoin).
        greetIdentity = null
        _sessionState.value = SessionState.Idle
    }

    // ═══════════════════════════════════════════════════════════════════
    // Host audio fan-out (BE §6 backpressure)
    // ═══════════════════════════════════════════════════════════════════

    /** Create a bounded outbound queue, continuous STREAM payload, and sender thread for a newly connected guest. */
    private fun startGuestAudioSender(endpointId: String) {
        val queue = GuestOutboundQueue(endpointId)
        guestQueues[endpointId] = queue
        // BE §4 — one long-lived STREAM payload per guest ("the continuous PCM audio
        // feed"), opened at connect time; sendAudioChunk writes framed records into it.
        transport.openAudioStream(endpointId)
        val thread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val chunk = queue.poll()
                    if (chunk != null) {
                        if (!transport.sendAudioChunk(endpointId, chunk)) {
                            // Continuous stream died (e.g. Nearby transfer failure) —
                            // re-open it so audio resumes; the queue's drop-oldest
                            // policy bounds what was lost in the gap.
                            transport.openAudioStream(endpointId)
                            // Backoff so a persistently dead link doesn't busy-spin.
                            // stopGuestAudioSender interrupts this thread — swallow the
                            // exception and let the loop condition end cleanly (BE §6).
                            try {
                                Thread.sleep(50)
                            } catch (_: InterruptedException) {
                                break
                            }
                        }
                    } else {
                        // Brief sleep when queue is empty to avoid busy-waiting.
                        // stopGuestAudioSender interrupts this thread — swallow the
                        // exception and let the loop condition end cleanly.
                        try {
                            Thread.sleep(5)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            } finally {
                Log.d(TAG, "Host: audio sender thread exited for $endpointId")
            }
        }, "HearYet-AudioSender-$endpointId").apply {
            isDaemon = true
            start()
        }
        guestSenderThreads[endpointId] = thread
        Log.d(TAG, "Host: started audio sender for $endpointId")
    }

    private fun stopGuestAudioSender(endpointId: String) {
        guestSenderThreads.remove(endpointId)?.interrupt()
        // Close the continuous STREAM payload for this guest (BE §4).
        transport.closeAudioStream(endpointId)
        guestQueues.remove(endpointId)
        Log.d(TAG, "Host: stopped audio sender for $endpointId")
    }

    /** BE §6 — distribute a PCM frame to every connected guest's outbound queue. */
    override fun onHostAudioChunk(hostTimestampNanos: Long, sequenceNumber: Long, pcmPayload: ByteArray) {
        val chunk = com.hearyet.app.feature.player.sync.AudioChunk(
            hostTimestampNanos = hostTimestampNanos,
            sequenceNumber = sequenceNumber,
            pcmPayload = pcmPayload,
        )
        // Copy payload per-queue to avoid sharing the byte array
        for ((endpointId, queue) in guestQueues) {
            if (queue.isChronicallyFull) {
                // BE §6 — a chronically full queue signals a degraded link: downgrade that
                // guest's SyncHealth to POOR (the guest's next DriftReport, or a recovering
                // queue, restores it). Never slow down the other guests.
                if (_hostGuests.value.any { it.endpointId == endpointId && it.syncHealth != SyncHealth.POOR }) {
                    Log.v(TAG, "Guest $endpointId chronically full (${queue.size}/${GuestOutboundQueue.MAX_QUEUED_CHUNKS}) — health → POOR")
                    _hostGuests.update { current ->
                        current.map { guest ->
                            if (guest.endpointId == endpointId && guest.syncHealth != SyncHealth.POOR) {
                                guest.copy(syncHealth = SyncHealth.POOR)
                            } else {
                                guest
                            }
                        }
                    }
                }
            }
            queue.enqueue(chunk.copy(pcmPayload = pcmPayload.copyOf()))
        }
    }

    override fun onSessionActive() {
        // DummySurfaceHelper activation is handled in PlayerService via SessionHolder
        Log.d(TAG, "Session active notification received")
    }

    override fun onSessionEnded() {
        Log.d(TAG, "Session ended notification received")
    }

    // ═══════════════════════════════════════════════════════════════════
    // Teardown
    // ═══════════════════════════════════════════════════════════════════

    /** Full teardown — stops advertising/discovery, disconnects all, stops sync pipeline. */
    fun teardown() {
        // C11 — Unregister from SessionHolder
        SessionHolder.active = null
        stopHeartbeat()
        // Cancel the initial sync batch job — its blocking body may still be
        // running, but its result callbacks are now gated on isActive (BE §10).
        syncPipelineJob?.cancel()
        syncPipelineJob = null

        // Stop sync pipeline in correct order: drift → scheduler → clock sync (BE §8, §7, §5)
        guestAudioFocusManager?.abandonFocus()
        guestAudioFocusManager = null
        driftCorrectionManager?.stop()
        driftCorrectionManager = null
        presentationScheduler?.stop()
        presentationScheduler = null
        clockSyncManager?.stopBackgroundResync()
        clockSyncManager = null
        guestGreetingManager?.release()
        guestGreetingManager = null
        bluetoothRouteManager?.stop()
        bluetoothRouteManager = null

        // BE §10 — stop guest foreground service if running
        context.stopService(
            android.content.Intent(context, com.hearyet.app.feature.session.guest.GuestSessionService::class.java)
        )

        // Stop per-guest audio sender threads and clear queues
        for (endpointId in guestSenderThreads.keys) {
            stopGuestAudioSender(endpointId)
        }

        transport.stopAdvertising()
        transport.stopDiscovery()
        transport.disconnectAll()
        transport.onEndpointConnected = null
        transport.onEndpointDisconnected = null
        transport.onConnectionResult = null
        transport.onHostDiscovered = null
        transport.onControlMessage = null
        transport.onAudioChunkReceived = null
        transport.onError = null
        connectedHostEndpointId = null
        pendingRejoinRequest = null
    }

    /**
     * Cancel session creation before it's fully live (dismiss Create sheet,
     * FE §9.4).  Must not leave half-created state hanging.
     */
    fun cancelSessionCreation() {
        if (_sessionState.value == SessionState.Advertising ||
            _sessionState.value == SessionState.WaitingForMedia
        ) {
            stopHeartbeat()
            clearSessionState()
            teardown()
            _sessionState.value = SessionState.Idle
            Log.d(TAG, "Session creation cancelled")
        }
    }

    /** Leave a session as Guest (FE §9.6). */
    override fun leaveSession() {
        if (role == SessionRole.Guest) {
            stopHeartbeat()
            clearSessionState()
            guestGreetingManager?.onSessionEnded()
            hasReachedPlayingThisSession = false
            teardown()
            _sessionState.value = SessionState.Idle
            Log.d(TAG, "Guest left session")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // C10.4 — Heartbeat (BE §10.1)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Host: start periodically broadcasting [ControlMessage.Heartbeat] to all
     * connected guests every [HEARTBEAT_INTERVAL_MS] so they can detect a silent
     * host death (BE §10.1).
     *
     * Guest: checks for HOST_UNREACHABLE every [HOST_REACHABILITY_CHECK_MS]
     * (1s granularity, per §17.9) to catch silent host death within 15s.
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            var lastHeartbeatSentMs = 0L
            while (isActive) {
                val now = System.nanoTime() / 1_000_000L

                // Host: broadcast heartbeat every 5s
                if (role == SessionRole.Host &&
                    now - lastHeartbeatSentMs >= HEARTBEAT_INTERVAL_MS
                ) {
                    lastHeartbeatSentMs = now
                    transport.broadcastControlMessage(
                        ControlMessage.Heartbeat(System.nanoTime())
                    )
                }

                // Guest: check for HOST_UNREACHABLE every 1s (BE §17.9)
                if (role == SessionRole.Guest && lastHostMessageNanos > 0) {
                    val elapsed = (System.nanoTime() - lastHostMessageNanos) / 1_000_000L
                    if (elapsed > HOST_UNREACHABLE_TIMEOUT_MS) {
                        Log.w(TAG, "Guest: host unreachable after ${elapsed}ms")
                        _sessionState.value = SessionState.Error(SessionError.HOST_UNREACHABLE)
                        stopHeartbeat()
                    }
                }

                delay(HOST_REACHABILITY_CHECK_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // ═══════════════════════════════════════════════════════════════════
    // C10.6 — Session persistence for rejoin across app restart (BE §10.2)
    // Now backed by DataStore (SessionDataStore) instead of SharedPreferences.
    // ═══════════════════════════════════════════════════════════════════

    private fun saveSessionState() {
        // BE §10.2 — persisted continuously while a session is active. SessionDataStore
        // is DataStore-backed with a synchronous surface (tiny reads/writes).
        sessionPrefs.putString(SessionDataStore.KEY_SESSION_ID, sessionId)
        sessionPrefs.putString(SessionDataStore.KEY_SESSION_CODE, sessionCode)
        sessionPrefs.putString(SessionDataStore.KEY_ROLE, when (role) {
            is SessionRole.Host -> "Host"
            is SessionRole.Guest -> "Guest"
        })
        sessionPrefs.putString(SessionDataStore.KEY_HOST_ENDPOINT_NAME, hostEndpointName)
        sessionPrefs.putString(SessionDataStore.KEY_DISPLAY_NAME, displayName)
        sessionPrefs.putString(SessionDataStore.KEY_PREVIOUS_ENDPOINT_ID, previousEndpointId)
    }

    /**
     * BE §10.2 — restore persisted session on app restart.
     * Reads are synchronous (DataStore read is tiny and runBlocking-funneled).
     * Returns true if a persisted Guest session was found.
     */
    fun tryRestoreSession(): Boolean {
        // SessionDataStore.getString() is synchronous — no coroutine/Flow needed.
        val savedRole = sessionPrefs.getString(SessionDataStore.KEY_ROLE) ?: return false

        role = when (savedRole) {
            "Host" -> SessionRole.Host
            "Guest" -> SessionRole.Guest
            else -> return false
        }

        sessionId = sessionPrefs.getString(SessionDataStore.KEY_SESSION_ID)
        sessionCode = sessionPrefs.getString(SessionDataStore.KEY_SESSION_CODE)
        hostEndpointName = sessionPrefs.getString(SessionDataStore.KEY_HOST_ENDPOINT_NAME)
        displayName = sessionPrefs.getString(SessionDataStore.KEY_DISPLAY_NAME) ?: displayName
        previousEndpointId = sessionPrefs.getString(SessionDataStore.KEY_PREVIOUS_ENDPOINT_ID)

        Log.d(TAG, "Restored session: role=$role, id=$sessionId, code=$sessionCode")
        return true
    }

    /**
     * Guest-only: after restore, start discovery for the saved host and
     * send a [RejoinRequest] once connected (BE §10.2 reconnect path).
     * Host path: clear persisted state and return false (no silent resume).
     */
    fun performRestoredGuestRejoin(): Boolean {
        if (role != SessionRole.Guest) {
            // Host cannot silently resume after process death (BE §10.2) — its playback
            // state is gone. Clear persisted state and route to Home.
            clearSessionState()
            // Do NOT reset role here — leave it as Host so caller can distinguish
            // the "host cleared" case from the "guest rejoined" case if needed.
            return false
        }
        val hostName = hostEndpointName ?: run {
            clearSessionState()
            return false
        }
        Log.d(TAG, "Guest: performing restore rejoin to $hostName")

        // Stage the RejoinRequest (previousEndpointId + displayName) so the
        // connection-result handler in startGuestDiscovery can send it immediately
        // after the fresh connection succeeds. The persisted identity must be captured
        // BEFORE discovery starts — the connection handler overwrites
        // previousEndpointId with the new endpointId as soon as it runs.
        val prevId = previousEndpointId
        if (prevId != null) {
            // BE §14.3 — the chime's rejoin identity is the persisted OLD id, captured
            // before discovery starts (the connection handler overwrites
            // previousEndpointId with the new connection ID as soon as it runs).
            greetIdentity = prevId
            pendingRejoinRequest = prevId to displayName
        }

        startGuestDiscovery(hostName)
        return true
    }

    /** BE §10.2 — clear all persisted session state. Accessible from nav graph. */
    fun clearSessionState() {
        sessionPrefs.clear()
    }

    // ── Rejoin on restart — BE §10.2 reconnect path ──────────────────

    private fun roleToKey(role: SessionRole): String = when (role) {
        is SessionRole.Host -> "Host"
        is SessionRole.Guest -> "Guest"
    }

    private fun keyToRole(key: String): SessionRole? = when (key) {
        "Host" -> SessionRole.Host
        "Guest" -> SessionRole.Guest
        else -> null
    }
}

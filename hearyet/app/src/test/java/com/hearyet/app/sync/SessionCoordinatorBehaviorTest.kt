package com.hearyet.app.sync

import android.app.Application
import android.content.Context
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.hearyet.app.core.model.SessionError
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.feature.permission.nearbyRuntimePermissions
import com.hearyet.app.feature.player.sync.AudioChunk
import com.hearyet.app.transport.ControlMessage
import com.hearyet.app.transport.NearbyTransportManager
import com.hearyet.app.transport.SessionPayload
import com.hearyet.app.transport.SessionPayloadCodec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * BE §4/§7/§10 — SessionCoordinator behavior with a mocked transport.
 *
 * Messages are delivered by invoking the coordinator's own wired callbacks
 * ([NearbyTransportManager.onControlMessage] etc.) — the exact route
 * production uses. Play-services availability is stubbed to SUCCESS so the
 * coordinator never bails to DEVICE_INCOMPATIBLE.
 *
 * The guest-pipeline test runs the REAL ClockSyncManager batch loop (with
 * responses injected through the manager's pendingSyncResponseQueue, the same
 * route the coordinator uses) — only the wire is faked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionCoordinatorBehaviorTest {

    private val HOST_ENDPOINT = "ep-host"
    private val GUEST_ENDPOINT = "ep-guest"

    private lateinit var context: Context
    private lateinit var transport: NearbyTransportManager

    // MockK does not store var-property assignments on mocks — the coordinator
    // WIRES its handlers by assigning these callbacks, so each property is made
    // "sticky": the setter is captured and the getter returns the captured value.
    private val onEndpointConnected = slot<(String, String) -> Unit>()
    private val onEndpointDisconnected = slot<(String) -> Unit>()
    private val onControlMessage = slot<(String, ControlMessage) -> Unit>()
    private val onConnectionResult = slot<(Boolean) -> Unit>()
    private val onHostDiscovered = slot<(String, String) -> Unit>()
    private val onAudioChunkReceived = slot<(AudioChunk) -> Unit>()
    private val onError = slot<(String) -> Unit>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context as Application).grantPermissions(*nearbyRuntimePermissions())
        // Mock the GMS singleton's INSTANCE method — stubbing only getInstance()
        // still runs the real isGooglePlayServicesAvailable, which reports
        // SERVICE_MISSING under Robolectric and bails the coordinator to
        // DEVICE_INCOMPATIBLE before any session code runs.
        val googleApi = GoogleApiAvailability.getInstance()
        mockkObject(googleApi)
        every {
            googleApi.isGooglePlayServicesAvailable(any())
        } returns ConnectionResult.SUCCESS
        transport = mockk<NearbyTransportManager>(relaxed = true)
        stickyProperties()
    }

    private fun stickyProperties() {
        every { transport.onEndpointConnected = capture(onEndpointConnected) } just Runs
        every { transport.onEndpointConnected } answers { onEndpointConnected.captured }
        every { transport.onEndpointDisconnected = capture(onEndpointDisconnected) } just Runs
        every { transport.onEndpointDisconnected } answers { onEndpointDisconnected.captured }
        every { transport.onControlMessage = capture(onControlMessage) } just Runs
        every { transport.onControlMessage } answers { onControlMessage.captured }
        every { transport.onConnectionResult = capture(onConnectionResult) } just Runs
        every { transport.onConnectionResult } answers { onConnectionResult.captured }
        every { transport.onHostDiscovered = capture(onHostDiscovered) } just Runs
        every { transport.onHostDiscovered } answers { onHostDiscovered.captured }
        every { transport.onAudioChunkReceived = capture(onAudioChunkReceived) } just Runs
        every { transport.onAudioChunkReceived } answers { onAudioChunkReceived.captured }
        every { transport.onError = capture(onError) } just Runs
        every { transport.onError } answers { onError.captured }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun newCoordinator(): SessionCoordinator =
        SessionCoordinator(context, transportOverride = transport)

    private fun encodedPayload(sessionId: String = "sid-1"): String {
        val code = "ABC123"
        return SessionPayloadCodec.encode(
            SessionPayload(
                sessionId = sessionId,
                sessionCode = code,
                hostEndpointName = SessionPayload.buildEndpointName(code),
                hostDisplayName = "Host",
            ),
        )
    }

    /** Let a guest reach ClockSyncing (connected, handshake sent, awaiting ack). */
    private fun connectGuest(coordinator: SessionCoordinator, sessionId: String = "sid-1") {
        coordinator.onQrScanned(encodedPayload(sessionId))
        onHostDiscovered.captured.invoke(
            HOST_ENDPOINT,
            SessionPayload.buildEndpointName("ABC123"),
        )
        onConnectionResult.captured.invoke(true)
        assertEquals(SessionState.ClockSyncing, coordinator.sessionState.value)
    }

    private fun deliverControlMessage(endpointId: String, message: ControlMessage) {
        onControlMessage.captured.invoke(endpointId, message)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitCondition(what: String, timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idleMainLooper()
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for: $what")
    }

    // ── Host side ────────────────────────────────────────────────────

    @Test
    fun host_rejoinRequest_replacesGuestEntryInPlaceAndReseeds() {
        val coordinator = newCoordinator()
        coordinator.startAsHost("Host")
        // Host is already Playing → the rejoined guest must be re-seeded.
        coordinator.onHostPlayPause(isPlaying = true, positionMs = 12_000)

        onEndpointConnected.captured.invoke("old-id", "Guest")
        onEndpointConnected.captured.invoke(GUEST_ENDPOINT, "Guest")

        deliverControlMessage(
            GUEST_ENDPOINT,
            ControlMessage.RejoinRequest(previousEndpointId = "old-id", displayName = "Guest"),
        )

        // BE §4 — replaced in place: same count, new endpointId, old one gone.
        assertEquals(2, coordinator.hostGuests.value.size)
        assertTrue(coordinator.hostGuests.value.any { it.endpointId == GUEST_ENDPOINT })
        assertFalse(coordinator.hostGuests.value.any { it.endpointId == "old-id" })

        // BE §4/§10.2 — the host immediately re-sends PlaybackState to the rejoined
        // guest (the latecomer-join send on connect + the rejoin re-seed are both
        // legitimate; at least one must carry the current position).
        verify(atLeast = 1) {
            transport.sendControlMessage(
                GUEST_ENDPOINT,
                match<ControlMessage.PlaybackState> { it.positionMs == 12_000L },
            )
        }
        coordinator.teardown()
    }

    @Test
    fun host_audioSenderInterruptedDuringSendBackoff_exitsWithoutCrashing() {
        // Device-verified regression (BE §6, L348): an interrupt landing inside
        // the failed-send backoff Thread.sleep used to propagate as an uncaught
        // InterruptedException on the "HearYet-AudioSender-*" thread and
        // FATAL-crash the entire host process mid-session.
        val uncaught = mutableListOf<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> uncaught += throwable }
        try {
            val coordinator = newCoordinator()
            coordinator.startAsHost("Host")
            onEndpointConnected.captured.invoke(GUEST_ENDPOINT, "Guest")

            // The relaxed mock returns false from sendAudioChunk, so the sender
            // hits the failed-send branch and parks in the backoff sleep(50).
            coordinator.onHostAudioChunk(
                hostTimestampNanos = 1_000_000L,
                sequenceNumber = 1L,
                pcmPayload = ByteArray(64),
            )
            awaitCondition("sender thread started") {
                Thread.getAllStackTraces().keys.any { it.name == "HearYet-AudioSender-$GUEST_ENDPOINT" }
            }
            Thread.sleep(120)

            // Disconnect interrupts the sender mid-backoff — must exit cleanly,
            // never crash the host process.
            onEndpointDisconnected.captured.invoke(GUEST_ENDPOINT)
            awaitCondition("sender thread exited") {
                Thread.getAllStackTraces().keys.none { it.name == "HearYet-AudioSender-$GUEST_ENDPOINT" }
            }

            assertEquals(emptyList<Throwable>(), uncaught)
            coordinator.teardown()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun host_driftReport_mapsHealthByThresholds() {
        val coordinator = newCoordinator()
        coordinator.startAsHost("Host")
        onEndpointConnected.captured.invoke(GUEST_ENDPOINT, "Guest")

        val reportDrift = { driftMs: Double ->
            deliverControlMessage(GUEST_ENDPOINT, ControlMessage.DriftReport(driftMs))
        }

        reportDrift(5.0)
        assertEquals(SyncHealth.GOOD, coordinator.hostGuests.value.single().syncHealth)
        reportDrift(20.0)
        assertEquals(SyncHealth.DEGRADED, coordinator.hostGuests.value.single().syncHealth)
        reportDrift(60.0)
        assertEquals(SyncHealth.POOR, coordinator.hostGuests.value.single().syncHealth)
        coordinator.teardown()
    }

    @Test
    fun host_latecomerJoin_sendsPlaybackStateImmediately() {
        val coordinator = newCoordinator()
        coordinator.startAsHost("Host")
        coordinator.onHostPlayPause(isPlaying = true, positionMs = 30_000)

        onEndpointConnected.captured.invoke(GUEST_ENDPOINT, "Guest")

        // BE §4 — sent on connection accept, not on the next natural sync tick.
        verify(exactly = 1) {
            transport.sendControlMessage(
                GUEST_ENDPOINT,
                match<ControlMessage.PlaybackState> { it.isPlaying && it.positionMs == 30_000L },
            )
        }
        coordinator.teardown()
    }

    @Test
    fun host_guestJoined_updatesDisplayName() {
        val coordinator = newCoordinator()
        coordinator.startAsHost("Host")
        onEndpointConnected.captured.invoke(GUEST_ENDPOINT, "placeholder")

        deliverControlMessage(
            GUEST_ENDPOINT,
            ControlMessage.GuestJoined(endpointId = GUEST_ENDPOINT, displayName = "Infinix"),
        )

        assertEquals("Infinix", coordinator.hostGuests.value.single().displayName)
        coordinator.teardown()
    }

    // ── Guest side: pre-sync messages ────────────────────────────────

    @Test
    fun guest_handshakeMismatch_rejectsWithConnectionFailed() {
        val coordinator = newCoordinator()
        connectGuest(coordinator)

        // Host reports a DIFFERENT sessionId than the QR payload advertised.
        deliverControlMessage(HOST_ENDPOINT, ControlMessage.SessionHandshakeAck(sessionId = "other-session"))

        assertEquals(
            SessionState.Error(SessionError.CONNECTION_FAILED),
            coordinator.sessionState.value,
        )
        // BE §4 — the misrouted connection is torn down.
        verify { transport.disconnect(HOST_ENDPOINT) }
        coordinator.teardown()
    }

    @Test
    fun guest_sessionEnded_transitionsToEnded() {
        val coordinator = newCoordinator()
        connectGuest(coordinator)

        deliverControlMessage(HOST_ENDPOINT, ControlMessage.SessionEnded)

        assertEquals(SessionState.Ended, coordinator.sessionState.value)
        coordinator.teardown()
    }

    @Test
    fun guest_leaveDuringClockSync_neverResurrectsSyncTimeout() {
        val coordinator = newCoordinator()
        // The wire would let the batch converge (~2s) — but the user leaves first.
        every { transport.sendControlMessage(any(), any<ControlMessage>()) } answers {
            val message = secondArg<ControlMessage>()
            if (message is ControlMessage.ClockSyncRequest) {
                coordinator.clockSyncManager?.pendingSyncResponseQueue?.offer(
                    ControlMessage.ClockSyncResponse(
                        t0 = message.t0,
                        t1 = message.t0 + 100_000_001L,
                        t2 = message.t0 + 100_000_002L,
                    ),
                )
            }
        }
        connectGuest(coordinator)
        deliverControlMessage(HOST_ENDPOINT, ControlMessage.SessionHandshakeAck(sessionId = "sid-1"))

        // Leave mid-batch: the blocking batch cannot be stopped, but its result
        // callbacks are gated on the cancelled job (BE §10) — no late state change.
        Thread.sleep(500)
        coordinator.leaveSession()
        assertEquals(SessionState.Idle, coordinator.sessionState.value)

        // Give the orphaned batch time to finish and try to fire its callback.
        Thread.sleep(3_000)
        assertEquals(
            "leaving must not resurrect a late SYNC_TIMEOUT/CONNECTED update",
            SessionState.Idle,
            coordinator.sessionState.value,
        )
    }

    @Test
    fun guest_noHostMessagesFor15s_transitionsToHostUnreachable() {
        val coordinator = newCoordinator()
        connectGuest(coordinator)

        // No handshake ack, no heartbeat. Robolectric virtualizes the looper clock
        // (which drives the heartbeat coroutine's delay) but NOT System.nanoTime
        // (which the coordinator uses to measure the gap), so each iteration must
        // advance BOTH clocks: idle the looper 1s and let 1.1s of real time pass.
        repeat(17) {
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            Thread.sleep(1_100)
        }

        assertEquals(
            SessionState.Error(SessionError.HOST_UNREACHABLE),
            coordinator.sessionState.value,
        )
        coordinator.teardown()
    }

    // ── Guest side: converged pipeline (BE §5 + §7 + §14) ────────────

    @Test
    fun guestPipeline_flushReseedSemantics_andChimeOncePerSession() {
        val greetingMock = mockk<GuestGreetingManager>(relaxed = true)
        val coordinator = SessionCoordinator(
            context,
            transportOverride = transport,
            guestGreetingManagerFactory = { greetingMock },
        )

        // The wire answers every ClockSyncRequest with a clean 100ms-bias response
        // through the SAME pendingSyncResponseQueue the coordinator uses.
        lateinit var coordinatorRef: SessionCoordinator
        every { transport.sendControlMessage(any(), any<ControlMessage>()) } answers {
            val message = secondArg<ControlMessage>()
            if (message is ControlMessage.ClockSyncRequest) {
                coordinatorRef.clockSyncManager?.pendingSyncResponseQueue?.offer(
                    ControlMessage.ClockSyncResponse(
                        t0 = message.t0,
                        t1 = message.t0 + 100_000_001L,
                        t2 = message.t0 + 100_000_002L,
                    ),
                )
            }
        }
        coordinatorRef = coordinator

        connectGuest(coordinator)
        // Complete the §4 handshake so the sync pipeline starts.
        deliverControlMessage(HOST_ENDPOINT, ControlMessage.SessionHandshakeAck(sessionId = "sid-1"))

        // Real batch loop converges in ~2s → onGuestSyncReady runs on Main.
        awaitCondition("sync pipeline ready") {
            coordinator.presentationScheduler?.isRunning == true
        }
        assertEquals(SessionState.Connected(0), coordinator.sessionState.value)
        verify(exactly = 1) { greetingMock.preload() }

        // ── First PlaybackState → Playing + exactly one chime (BE §14) ──
        // BE §6:356 — the chime volume is the guest's local volume state, live.
        coordinator.guestVolumeState.set(0.4f, audioTrack = null)
        deliverControlMessage(
            HOST_ENDPOINT,
            ControlMessage.PlaybackState(isPlaying = true, positionMs = 5_000, sharedClockTimestampNanos = 1L),
        )
        assertEquals(SessionState.Playing(5_000), coordinator.sessionState.value)
        val greetVolume = slot<Float>()
        verify(exactly = 1) { greetingMock.maybeGreet(any(), capture(greetVolume)) }
        assertEquals(0.4f, greetVolume.captured, 0f)

        // BE §14.4.2/§17.13 — the greeted identity is persisted (survives process death).
        assertEquals(
            "ep-host",
            SessionDataStore(context).getString(SessionDataStore.KEY_GREETED_IDENTITY),
        )

        // ── BE §6:352 — transient focus handlers duck without disturbing sync ──
        val focusManager = coordinator.guestAudioFocusManager
        assertNotNull(focusManager)
        focusManager?.onTransientFocusDuck?.invoke()
        focusManager?.onTransientFocusLost?.invoke()
        focusManager?.onFocusRegained?.invoke()
        assertEquals(SessionState.Playing(5_000), coordinator.sessionState.value)
        assertTrue(coordinator.presentationScheduler?.isRunning == true)

        // ── Fill the scheduler's ring buffer with two chunks ──
        val chunk = { seq: Long ->
            AudioChunk(
                hostTimestampNanos = System.nanoTime(),
                sequenceNumber = seq,
                pcmPayload = ByteArray(3_840),
            )
        }
        onAudioChunkReceived.captured.invoke(chunk(1))
        onAudioChunkReceived.captured.invoke(chunk(2))
        assertEquals(2, coordinator.presentationScheduler?.bufferSize)

        // ── D4: MediaChanged flushes ONLY — the scheduler stays alive ──
        deliverControlMessage(
            HOST_ENDPOINT,
            ControlMessage.MediaChanged(mediaTitle = "next.mp4", sharedClockTimestampNanos = 2L),
        )
        assertNotNull(coordinator.presentationScheduler)
        assertEquals(0, coordinator.presentationScheduler?.bufferSize)
        assertEquals(0L, coordinator.presentationScheduler?.chunksPlayed)
        assertTrue(coordinator.presentationScheduler?.isRunning == true)
        assertTrue(coordinator.presentationScheduler?.isAudioTrackReady == true)

        // ── Reseed happens on the new PlaybackState (flush-and-reseed, still alive) ──
        deliverControlMessage(
            HOST_ENDPOINT,
            ControlMessage.PlaybackState(isPlaying = true, positionMs = 0L, sharedClockTimestampNanos = 3L),
        )
        assertEquals(SessionState.Playing(0), coordinator.sessionState.value)
        assertEquals(0, coordinator.presentationScheduler?.bufferSize)

        // ── Second PlaybackState must NOT re-greet (once per session) ──
        verify(exactly = 1) { greetingMock.maybeGreet(any(), any()) }

        // ── SeekTo → flush-and-reseed like a seek (BE §7) ──
        onAudioChunkReceived.captured.invoke(chunk(3))
        assertEquals(1, coordinator.presentationScheduler?.bufferSize)
        deliverControlMessage(
            HOST_ENDPOINT,
            ControlMessage.SeekTo(positionMs = 60_000, sharedClockTimestampNanos = 4L),
        )
        assertEquals(0, coordinator.presentationScheduler?.bufferSize)
        assertEquals(SessionState.Playing(60_000), coordinator.sessionState.value)

        // ── BE §14.3/§14.4.2 — restore rejoin reuses the persisted greet identity:
        //    seedGreetedIdentity must receive the same identity that was persisted
        //    when the chime first played, so the rejoin is never re-greeted. ──
        assertTrue(coordinator.performRestoredGuestRejoin())
        verify(exactly = 1) { greetingMock.seedGreetedIdentity("ep-host") }

        // ── Session end resets the greeting state (BE §14.3) ──
        deliverControlMessage(HOST_ENDPOINT, ControlMessage.SessionEnded)
        verify(exactly = 1) { greetingMock.onSessionEnded() }
        // The persisted greeted identity is cleared with the rest of the session
        // state, so a NEW session greets again (§14.4.1/§14.4.3).
        assertNull(SessionDataStore(context).getString(SessionDataStore.KEY_GREETED_IDENTITY))
    }
}

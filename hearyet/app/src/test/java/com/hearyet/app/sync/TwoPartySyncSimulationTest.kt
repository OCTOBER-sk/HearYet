package com.hearyet.app.sync

import com.hearyet.app.transport.ControlMessage
import com.hearyet.app.transport.NearbyTransportManager
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BE §5 — full clock-exchange simulation: a REAL host-side ClockSyncManager
 * (handleSyncRequest) and REAL guest-side batches on each end of a faked wire.
 * Only the transport is faked; every timestamp, formula, slot, and deadline is
 * production code.
 *
 * The wire simulates the host being `shift` nanos ahead of the guest by
 * shifting the host's captured t1/t2 before delivery — so the guest's
 * estimate must recover exactly that injected offset.
 */
@RunWith(RobolectricTestRunner::class)
class TwoPartySyncSimulationTest {

    private val HOST_EP = "host-endpoint"
    private val BIAS_NANOS = 100_000_000L

    private class SimulatedHost {
        val responseCounter = AtomicInteger(0)
        lateinit var biasProvider: (Int) -> Long
        val guestManagers = ConcurrentHashMap<String, ClockSyncManager>()

        /** The host's transport: this is the WIRE between the two real managers. */
        val transport = mockk<NearbyTransportManager>(relaxed = true)

        init {
            every { transport.sendControlMessage(any(), any<ControlMessage>()) } answers {
                val endpoint = firstArg<String>()
                val response = secondArg<ControlMessage>() as ControlMessage.ClockSyncResponse
                val index = responseCounter.getAndIncrement()
                val shift = biasProvider(index)
                // Wire-level host-clock shift: t1/t2 moved `shift` ahead.
                guestManagers[endpoint]?.pendingSyncResponseQueue?.offer(
                    ControlMessage.ClockSyncResponse(
                        t0 = response.t0,
                        t1 = response.t1 + shift,
                        t2 = response.t2 + shift,
                    ),
                )
            }
        }

        /**
         * Create a REAL guest manager whose transport routes every ClockSyncRequest
         * into the REAL host's handleSyncRequest — the full §5 exchange.
         */
        fun newGuest(guestEndpoint: String): ClockSyncManager {
            val guestTransport = mockk<NearbyTransportManager>(relaxed = true)
            val guestManager = ClockSyncManager(guestTransport)
            guestManagers[guestEndpoint] = guestManager
            every { guestTransport.sendControlMessage(any(), any<ControlMessage>()) } answers {
                val request = secondArg<ControlMessage>() as ControlMessage.ClockSyncRequest
                host.handleSyncRequest(guestEndpoint, request)
            }
            return guestManager
        }

        val host: ClockSyncManager = ClockSyncManager(transport)
    }

    private fun simulatedHost(biasProvider: (Int) -> Long): SimulatedHost =
        SimulatedHost().apply { this.biasProvider = biasProvider }

    private data class BatchOutcome(
        val elapsedMs: Long,
        val requests: Int,
        val offsetNanos: Double,
        val stddevMs: Double,
    )

    private fun runBatch(
        manager: ClockSyncManager,
        host: SimulatedHost,
        endpoint: String,
    ): BatchOutcome {
        val startNanos = System.nanoTime()
        var offsetNanos = Double.NaN
        var stddevMs = Double.NaN
        manager.performSyncBatch(endpoint) { offset, stddev ->
            offsetNanos = offset
            stddevMs = stddev
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        return BatchOutcome(
            elapsedMs = elapsedMs,
            requests = host.responseCounter.get(),
            offsetNanos = offsetNanos,
            stddevMs = stddevMs,
        )
    }

    @Test
    fun twoParty_fastLink_recoversInjectedHostOffsetAndSpansTwoSeconds() {
        val host = simulatedHost({ BIAS_NANOS })
        val guest = host.newGuest("guest-a")

        val outcome = runBatch(guest, host, "guest-a")

        // Fix A at the exchange level: 10 probes × 200ms slots — the batch spans
        // ~2s even though this wire answered every probe instantly.
        assertEquals(10, outcome.requests)
        assertTrue("two-party batch must span ~2s (was ${outcome.elapsedMs}ms)", outcome.elapsedMs in 1_800..6_000)

        // The full exchange (request → real host capture → response → real guest
        // math) recovered the injected 100ms host-clock lead.
        assertTrue(guest.isSynced)
        assertEquals(100.0, outcome.offsetNanos / 1_000_000.0, 5.0)
        assertTrue("gate stddev under 5ms (was ${outcome.stddevMs})", outcome.stddevMs < 5.0)
    }

    @Test
    fun twoParty_jitteredLink_timesOutHonestly_thenDegradedOptInEnters() {
        // Alternating 90/110ms host shifts → batch stddev ≈ 10ms → never converges.
        val host = simulatedHost({ index ->
            if (index % 2 == 0) 110_000_000L else 90_000_000L
        })
        val guest = host.newGuest("guest-b")
        var timedOut = false
        guest.onSyncTimeout = { timedOut = true }

        val startNanos = System.nanoTime()
        val outcome = runBatch(guest, host, "guest-b")
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

        // Honest SYNC_TIMEOUT after ~10s with the degraded path OFF by default.
        assertTrue(elapsedMs >= 9_000)
        assertFalse(guest.isSynced)
        assertTrue(timedOut)
        assertEquals(-1.0, outcome.offsetNanos, 0.0)
        assertTrue(
            "jittered batch stddev in the degraded range (was ${outcome.stddevMs})",
            outcome.stddevMs in 5.0..15.0,
        )

        // Same link, calibration-justified opt-in ON (BE §5 L304): after the
        // 10s deadline, entry is allowed with POOR marking.
        ClockSyncManager.DEGRADED_MODE_ENABLED = true
        try {
            val degradedOutcome = runBatch(guest, host, "guest-b")
            assertTrue(guest.isSynced)
            assertTrue(guest.lastSyncDegraded)
            assertTrue(degradedOutcome.offsetNanos > 0)
            assertTrue(degradedOutcome.stddevMs <= 15.0)
        } finally {
            ClockSyncManager.DEGRADED_MODE_ENABLED = false
        }
    }

    @Test
    fun twoParty_monteCarlo_tenParallelBatchesAllRecoverTheOffset() {
        val host = simulatedHost({ BIAS_NANOS })
        val latches = mutableListOf<CountDownLatch>()
        val results = ConcurrentHashMap<String, BatchOutcome>()
        val threads = (1..10).map { i ->
            val endpoint = "guest-$i"
            val guest = host.newGuest(endpoint)
            val latch = CountDownLatch(1)
            latches.add(latch)
            Thread {
                results[endpoint] = runBatch(guest, host, endpoint)
                latch.countDown()
            }.apply {
                name = "two-party-guest-$i"
                start()
            }
        }

        // All 10 real batches converge in parallel (~2s each, wall ≈ 3s).
        latches.forEach { assertTrue(it.await(15, TimeUnit.SECONDS)) }
        threads.forEach { it.join(5_000) }

        assertEquals(10, results.size)
        results.forEach { (endpoint, outcome) ->
            assertTrue(
                "$endpoint converged (stddev=${"%.3f".format(outcome.stddevMs)}ms)",
                outcome.stddevMs < 5.0,
            )
            assertEquals(
                "$endpoint recovered the injected 100ms offset",
                100.0,
                outcome.offsetNanos / 1_000_000.0,
                5.0,
            )
        }
    }
}

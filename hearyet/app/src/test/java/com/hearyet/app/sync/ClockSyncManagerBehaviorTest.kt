package com.hearyet.app.sync

import com.hearyet.app.transport.ControlMessage
import com.hearyet.app.transport.NearbyTransportManager
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BE §5 — behavior of the real [ClockSyncManager.performSyncBatch] loop with a
 * mocked wire (only [NearbyTransportManager.sendControlMessage] is stubbed;
 * every response is delivered through [ClockSyncManager.pendingSyncResponseQueue],
 * the exact route the production coordinator uses).
 *
 * The "wire" answers each ClockSyncRequest synchronously with a constructed
 * ClockSyncResponse, so the RTT is ~0 and the measured offset is exactly the
 * injected bias — making the batch's stddev fully deterministic:
 * - constant bias  → stddev ≈ 0 (converges)
 * - alternating 90/110 ms bias → stddev ≈ 10 ms (never converges; degraded range)
 *
 * Real time costs: the 10-sample slot loop is 2 s per batch and the sync
 * deadline is 10 s (constants are locked by the spec), so the deadline tests
 * run for ~10 s each.
 */
@RunWith(RobolectricTestRunner::class)
class ClockSyncManagerBehaviorTest {

    private val HOST_ENDPOINT = "host-endpoint-1"
    private val BIAS_CLEAN_NANOS = 100_000_000L
    private val BIAS_NOISY_HI_NANOS = 110_000_000L
    private val BIAS_NOISY_LO_NANOS = 90_000_000L
    private val BIAS_OUTLIER_NANOS = 130_000_000L

    /**
     * The fake wire. [manager] is set after the [ClockSyncManager] is created,
     * before any probe is sent, so the answer always delivers into the queue of
     * the manager under test (the same route the production coordinator uses).
     */
    private class Wire {
        val requestsSent = AtomicInteger(0)
        lateinit var manager: ClockSyncManager
        var biasProvider: (Int) -> Long = { 0L }
        var onOutlierRequest: ((Int) -> Unit)? = null

        fun transport(): NearbyTransportManager {
            val transport = mockk<NearbyTransportManager>(relaxed = true)
            every {
                transport.sendControlMessage("host-endpoint-1", any<ControlMessage>())
            } answers {
                val request = secondArg<ControlMessage>() as ControlMessage.ClockSyncRequest
                val index = requestsSent.getAndIncrement()
                onOutlierRequest?.invoke(index)
                val bias = biasProvider(index)
                // Host clock is `bias` ahead of the guest: t1 = t0 + bias (+ε), t2 = t1 + ε.
                val response = ControlMessage.ClockSyncResponse(
                    t0 = request.t0,
                    t1 = request.t0 + bias + 1L,
                    t2 = request.t0 + bias + 2L,
                )
                manager.pendingSyncResponseQueue?.offer(response)
            }
            return transport
        }
    }

    private fun wireWith(biasProvider: (Int) -> Long): Wire {
        val wire = Wire()
        wire.biasProvider = biasProvider
        wire.manager = ClockSyncManager(wire.transport())
        return wire
    }

    @Test
    fun fastLink_tenProbes_spanAboutTwoSecondsAndConvergeWithInjectedOffset() {
        val wire = wireWith({ BIAS_CLEAN_NANOS })
        val manager = wire.manager
        var resultOffsetNanos = Double.NaN
        var resultStddevMs = Double.NaN

        val startNanos = System.nanoTime()
        manager.performSyncBatch(HOST_ENDPOINT) { offset, stddev ->
            resultOffsetNanos = offset
            resultStddevMs = stddev
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

        // Fix A — the batch is 10 probes × 200 ms slots: ~2 s by construction,
        // even though this "link" answered every probe instantly.
        assertEquals(10, wire.requestsSent.get())
        assertTrue("batch must span ~2s even on an instant link (was ${elapsedMs}ms)", elapsedMs in 1_800..6_000)

        // The estimate recovers the injected 100 ms bias, the gate passed, and
        // this was a normal (non-degraded) convergence.
        assertTrue(manager.isSynced)
        assertFalse(manager.lastSyncDegraded)
        assertEquals(100.0, resultOffsetNanos / 1_000_000.0, 5.0)
        assertTrue("converged batch stddev must be under the 5ms gate (was ${resultStddevMs})", resultStddevMs < 5.0)
    }

    @Test
    fun noisyFirstBatch_cleanSecondBatch_rebatchesUntilConverged() {
        val wire = wireWith { index ->
            // Batch 1 (requests 0..9) is noisy; batch 2 (10..19) is clean.
            if (index < 10) {
                if (index % 2 == 0) BIAS_NOISY_HI_NANOS else BIAS_NOISY_LO_NANOS
            } else {
                BIAS_CLEAN_NANOS
            }
        }
        val manager = wire.manager
        var resultOffsetNanos = Double.NaN

        manager.performSyncBatch(HOST_ENDPOINT) { offset, _ ->
            resultOffsetNanos = offset
        }

        // Two full batches of 10 probes were needed before the gate passed.
        assertEquals(20, wire.requestsSent.get())
        assertTrue(manager.isSynced)
        assertEquals(100.0, resultOffsetNanos / 1_000_000.0, 5.0)
    }

    @Test
    fun noisyLink_neverConverges_deadlineGivesHonestSyncTimeout() {
        val wire = wireWith { index ->
            if (index % 2 == 0) BIAS_NOISY_HI_NANOS else BIAS_NOISY_LO_NANOS
        }
        val manager = wire.manager
        var timedOut = false
        var resultOffsetNanos = Double.NaN
        var resultStddevMs = Double.NaN
        manager.onSyncTimeout = { timedOut = true }

        val startNanos = System.nanoTime()
        manager.performSyncBatch(HOST_ENDPOINT) { offset, stddev ->
            resultOffsetNanos = offset
            resultStddevMs = stddev
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

        // Honest SYNC_TIMEOUT after ~10s — never a silent success.
        assertTrue(elapsedMs >= 9_000)
        assertFalse(manager.isSynced)
        assertFalse(manager.lastSyncDegraded)
        assertTrue(timedOut)
        assertEquals(-1.0, resultOffsetNanos, 0.0)
        assertTrue(
            "timeout must report the batch stddev (was ${resultStddevMs})",
            resultStddevMs in 5.0..15.0,
        )
    }

    @Test
    fun degradedOptIn_flagOn_noisyLink_entersWithPoorMarkingAfterDeadline() {
        val wire = wireWith { index ->
            if (index % 2 == 0) BIAS_NOISY_HI_NANOS else BIAS_NOISY_LO_NANOS
        }
        val manager = wire.manager
        var resultOffsetNanos = Double.NaN
        var resultStddevMs = Double.NaN
        ClockSyncManager.DEGRADED_MODE_ENABLED = true
        try {
            val startNanos = System.nanoTime()
            manager.performSyncBatch(HOST_ENDPOINT) { offset, stddev ->
                resultOffsetNanos = offset
                resultStddevMs = stddev
            }
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

            // §5 — degraded entry is only sanctioned "after 10 seconds".
            assertTrue(elapsedMs >= 10_000)
            assertTrue(manager.isSynced)
            assertTrue(manager.lastSyncDegraded)
            assertTrue(resultOffsetNanos > 0)
            assertTrue(
                "degraded stddev must sit under the 15ms ceiling (was ${resultStddevMs})",
                resultStddevMs <= 15.0,
            )
        } finally {
            ClockSyncManager.DEGRADED_MODE_ENABLED = false
        }
    }

    @Test
    fun fixB_interruptBeforeDeadline_neverAcceptsDegradedSync() {
        val wire = wireWith { index ->
            if (index % 2 == 0) BIAS_NOISY_HI_NANOS else BIAS_NOISY_LO_NANOS
        }
        val manager = wire.manager
        var resultOffsetNanos = Double.NaN
        ClockSyncManager.DEGRADED_MODE_ENABLED = true
        try {
            val batchThread = Thread {
                try {
                    manager.performSyncBatch(HOST_ENDPOINT) { offset, _ ->
                        resultOffsetNanos = offset
                    }
                } catch (_: InterruptedException) {
                    // Teardown-style interrupt while blocked in a queue poll:
                    // the batch exits without firing any callback.
                }
            }
            batchThread.start()
            // Let one full noisy batch run (~2s), then tear the batch down.
            Thread.sleep(2_600)
            batchThread.interrupt()
            batchThread.join(5_000)

            assertFalse(batchThread.isAlive)
            // Degraded entry requires the 10s deadline to have actually elapsed —
            // an interrupt-driven teardown exit must never accept degraded sync,
            // regardless of whether the interrupt hit a slot sleep (clean exit via
            // the timeout decision → onResult(-1)) or a queue poll (InterruptedException).
            assertFalse(manager.isSynced)
            assertFalse(manager.lastSyncDegraded)
            assertTrue(resultOffsetNanos.isNaN() || resultOffsetNanos == -1.0)
        } finally {
            ClockSyncManager.DEGRADED_MODE_ENABLED = false
        }
    }

    @Test
    fun startBackgroundResync_threadInterruptedDuringSleep_exitsWithoutThrowing() {
        // CR1 — guest-leave crash regression: after sync converges, the guest runs a
        // background re-sync thread that sleeps 30–60s. Every teardown calls
        // stopBackgroundResync() → interrupt(). The old code left that interrupt
        // uncaught inside Thread.sleep → GlobalExceptionHandler → CrashActivity.
        val manager = ClockSyncManager(mockk<NearbyTransportManager>(relaxed = true))

        val uncaught = AtomicReference<Throwable?>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> uncaught.set(throwable) }
        try {
            manager.startBackgroundResync(HOST_ENDPOINT)
            // Let the thread reach its 30–60s sleep, then interrupt it the exact way
            // SessionCoordinator.teardown() does (stopBackgroundResync → interrupt).
            Thread.sleep(300)
            manager.stopBackgroundResync()
            Thread.sleep(300)

            assertNull("interrupted resync thread must exit without crashing (was ${uncaught.get()})", uncaught.get())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
            manager.stopBackgroundResync()
        }
    }

    @Test
    fun d3_gateUsesAllBatchSamples_notJustLowestRttSeventyFive() {
        // Every batch of ≥9 samples contains exactly one outlier (index % 9 == 8),
        // so even a batch truncated by the 10s deadline cannot become clean:
        // - 8 clean samples at the 100ms bias, lowest RTT (kept by the filter)
        // - 1 sample at the 130ms bias, deliberately delayed ~120ms so its RTT
        //   is the worst in the batch (excluded from the 75% keep)
        val wire = Wire()
        wire.biasProvider = { index ->
            if (index % 9 == 8) BIAS_OUTLIER_NANOS else BIAS_CLEAN_NANOS
        }
        wire.onOutlierRequest = { index ->
            if (index % 9 == 8) Thread.sleep(120)
        }
        wire.manager = ClockSyncManager(wire.transport())
        val manager = wire.manager
        var timedOut = false
        var resultStddevMs = Double.NaN
        manager.onSyncTimeout = { timedOut = true }

        val startNanos = System.nanoTime()
        manager.performSyncBatch(HOST_ENDPOINT) { _, stddev ->
            resultStddevMs = stddev
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

        // A gate that measured only the kept 75% subset would have converged
        // after the first batch (~2s). The real gate measures EVERY sample:
        // the run lasts the full ~10s and ends in an honest SYNC_TIMEOUT.
        assertTrue("a subset gate would have converged in ~2s (was ${elapsedMs}ms)", elapsedMs >= 9_000)
        assertFalse(manager.isSynced)
        assertFalse(manager.lastSyncDegraded)
        assertTrue(timedOut)
        assertNotNull(resultStddevMs)
        assertTrue(
            "full-batch stddev with one outlier must exceed the 5ms gate (was ${resultStddevMs})",
            resultStddevMs > 5.0,
        )
    }
}

package com.hearyet.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** BE §5 — Cristian's-algorithm clock math (computeOffset / estimateFromSamples). */
@RunWith(RobolectricTestRunner::class)
class ClockSyncManagerTest {

    @Test
    fun computeOffset_symmetricRtt_recoversHostOffset() {
        // Guest sends at t0=0; host (clock 1000ms ahead) receives at t1=1005,
        // replies at t2=1006; guest receives at t3=11.
        val (offsetNanos, rttNanos) = ClockSyncManager.computeOffset(
            t0 = 0L, t1 = 1_005L, t2 = 1_006L, t3 = 11L,
        )

        assertEquals(1_000.0, offsetNanos, 0.001) // host is 1000ms ahead
        assertEquals(10L, rttNanos) // 10ms round trip
    }

    @Test
    fun computeOffset_perfectSymmetric_rttIsTwoWayDelay() {
        // No offset between clocks; 5ms each way.
        val (offsetNanos, rttNanos) = ClockSyncManager.computeOffset(
            t0 = 0L, t1 = 5L, t2 = 6L, t3 = 11L,
        )

        assertEquals(0.0, offsetNanos, 0.001)
        assertEquals(10L, rttNanos)
    }

    @Test
    fun estimateFromSamples_discardsTopQuarterByRttAndTakesMedian() {
        // The (2000, 500) outlier has the worst RTT and must be discarded.
        val samples = listOf(
            1_000.0 to 10L,
            1_010.0 to 8L,
            990.0 to 12L,
            2_000.0 to 500L,
            1_005.0 to 9L,
            995.0 to 11L,
            1_002.0 to 7L,
            1_008.0 to 6L,
        )

        val estimate = ClockSyncManager.estimateFromSamples(samples)

        // Kept offsets (lowest 6 RTT): [1008,1002,1010,1005,1000,995] -> median = 1005.
        assertEquals(1_005.0, estimate, 0.001)
    }

    @Test
    fun estimateFromSamples_twoSamples_fallsBackToLowestRtt() {
        val samples = listOf(
            1_000.0 to 10L,
            1_100.0 to 500L,
        )

        // keepCount = 1 → use the lowest-RTT sample directly.
        assertEquals(1_000.0, ClockSyncManager.estimateFromSamples(samples), 0.001)
    }

    @Test
    fun estimateFromSamples_singleSample_returnsItsOffset() {
        assertEquals(
            777.0,
            ClockSyncManager.estimateFromSamples(listOf(777.0 to 42L)),
            0.001,
        )
    }
}

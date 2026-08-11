package com.hearyet.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** BE §7 — scheduler timing math (guestPlaybackTimeNanos, lookahead defaults). */
@RunWith(RobolectricTestRunner::class)
class PresentationSchedulerTest {

    @Test
    fun guestPlaybackTimeNanos_appliesClockOffsetAndLookahead() {
        val scheduler = PresentationScheduler()
        scheduler.clockOffsetNanos = 1_000_000.0 // host 1ms ahead
        scheduler.lookaheadMs = PresentationScheduler.DEFAULT_LOOKAHEAD_MS // 250ms

        val hostTimestampNanos = 1_000L
        val expected = hostTimestampNanos + 1_000_000L + (250 * 1_000_000L)

        assertEquals(
            expected,
            scheduler.guestPlaybackTimeNanos(hostTimestampNanos),
        )
    }

    @Test
    fun guestPlaybackTimeNanos_zeroOffset_noLookahead() {
        val scheduler = PresentationScheduler()
        scheduler.clockOffsetNanos = 0.0
        scheduler.lookaheadMs = 0

        assertEquals(5_000L, scheduler.guestPlaybackTimeNanos(5_000L))
    }

    @Test
    fun defaultLookahead_is250ms() {
        assertEquals(250, PresentationScheduler.DEFAULT_LOOKAHEAD_MS)
    }

    @Test
    fun scheduler_defaultsTo48kHzStereo() {
        assertEquals(48_000, PresentationScheduler().sampleRateHz)
        assertEquals(2, PresentationScheduler().channelCount)
    }
}

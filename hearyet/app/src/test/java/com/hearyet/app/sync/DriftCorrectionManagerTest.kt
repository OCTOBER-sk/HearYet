package com.hearyet.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** BE §8 — drift math (AudioTrack playback head vs. wall-clock expectation). */
class DriftCorrectionManagerTest {

    @Test
    fun perfectSync_atSampleRate_isZeroDrift() {
        // 1.5 s of 48 kHz audio presented at exactly the sample rate.
        val headFrames = 48_000L * 1500 / 1000 // 72,000 frames
        val elapsedNanos = 1_500_000_000L

        assertEquals(
            0.0,
            DriftCorrectionManager.computeDriftMs(headFrames, 0L, elapsedNanos, 48_000),
            0.001,
        )
    }

    @Test
    fun stereoChannelCount_doesNotAffectDriftMeasurement() {
        // Regression: the old formula multiplied by channelCount, which for stereo
        // reported ~-1000 ms of false drift per second of playback.
        val headFrames = 48_000L // 1 second at 48 kHz
        val elapsedNanos = 1_000_000_000L

        assertEquals(
            0.0,
            DriftCorrectionManager.computeDriftMs(headFrames, 0L, elapsedNanos, 48_000),
            0.001,
        )
    }

    @Test
    fun slowPresentation_showsNegativeDrift() {
        // Presented 1% slow over 10 s: 475,200 frames instead of 480,000 → -100 ms.
        val headFrames = 475_200L
        val elapsedNanos = 10_000_000_000L

        assertEquals(
            -100.0,
            DriftCorrectionManager.computeDriftMs(headFrames, 0L, elapsedNanos, 48_000),
            0.5,
        )
    }

    @Test
    fun baselineOffset_cancelsOut() {
        // Same as perfectSync but with a nonzero baseline — the offset must cancel.
        val baseline = 100_000L
        val headFrames = baseline + 72_000L
        val elapsedNanos = 1_500_000_000L

        assertEquals(
            0.0,
            DriftCorrectionManager.computeDriftMs(headFrames, baseline, elapsedNanos, 48_000),
            0.001,
        )
    }
}

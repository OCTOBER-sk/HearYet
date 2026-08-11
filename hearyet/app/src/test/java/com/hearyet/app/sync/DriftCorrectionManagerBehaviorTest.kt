package com.hearyet.app.sync

import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.feature.player.sync.AudioChunk
import com.hearyet.app.transport.NearbyTransportManager
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BE §8 — DriftCorrectionManager behavior with a real evaluation thread and a
 * Robolectric-shadowed AudioTrack:
 * - a paused track is never measured (no false drift),
 * - the shadow's playback head never advances, so once the baseline is set the
 *   second eval sees |drift| ≫ SEVERE_DESYNC_THRESHOLD_MS (150ms) and the
 *   hard-resync path fires deterministically,
 * - degraded-mode entry marks health POOR from the start.
 */
@RunWith(RobolectricTestRunner::class)
class DriftCorrectionManagerBehaviorTest {

    private val scheduler = PresentationScheduler()
    private val driftManager = DriftCorrectionManager(mockk<NearbyTransportManager>(relaxed = true))

    @After
    fun tearDown() {
        driftManager.stop()
        scheduler.stop()
    }

    @Test
    fun pausedTrack_isNeverMeasuredAsDrift() {
        assertTrue(scheduler.openAudioTrack())
        // Paused before the drift manager starts: the eval loop must skip.
        scheduler.getAudioTrack()?.pause()

        driftManager.start(requireNotNull(scheduler.getAudioTrack()), scheduler, "host-ep")

        // Two eval cycles pass (1.5s each) — no correction, no health change.
        Thread.sleep(3_500)
        assertEquals(0.0, driftManager.driftMs, 0.0)
        assertEquals(SyncHealth.GOOD, driftManager.syncHealth)
        assertEquals(1.0f, driftManager.currentPlaybackSpeed, 0.0001f)
    }

    @Test
    fun severeDesync_triggersHardResyncFlushingTheScheduler() {
        assertTrue(scheduler.openAudioTrack())
        scheduler.start()
        // A far-future chunk sits in the ring buffer — the hard resync must flush it.
        scheduler.onChunkReceived(
            AudioChunk(
                hostTimestampNanos = System.nanoTime() + 10_000_000_000L,
                sequenceNumber = 1L,
                pcmPayload = ByteArray(3_840),
            ),
        )
        assertEquals(1, scheduler.bufferSize)

        driftManager.start(requireNotNull(scheduler.getAudioTrack()), scheduler, "host-ep")

        // First eval (~1.5s) establishes the baseline at the current head (0).
        Thread.sleep(2_000)
        assertEquals(SyncHealth.GOOD, driftManager.syncHealth)

        // Second eval (~3s): the shadow head stays at 0 while the schedule expects
        // ~1.5s of frames → drift ≈ -1500ms ≫ SEVERE_DESYNC_THRESHOLD_MS.
        Thread.sleep(2_000)

        // BE §8 — hard resync: POOR health, speed restored, scheduler flushed.
        assertEquals(SyncHealth.POOR, driftManager.syncHealth)
        assertEquals(1.0f, driftManager.currentPlaybackSpeed, 0.0001f)
        assertEquals(0, scheduler.bufferSize)
        assertEquals(0L, scheduler.chunksPlayed)
    }

    @Test
    fun markDegradedEntry_flipsHealthToPoorFromTheStart() {
        assertEquals(SyncHealth.GOOD, driftManager.syncHealth)
        driftManager.markDegradedEntry()
        assertEquals(SyncHealth.POOR, driftManager.syncHealth)
    }
}

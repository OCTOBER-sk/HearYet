package com.hearyet.app.sync

import com.hearyet.app.feature.player.sync.AudioChunk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BE §7 — PresentationScheduler behavior with a real playback thread and a
 * Robolectric-shadowed AudioTrack: due chunks get written, chunks late by more
 * than one frame get dropped, and flush() resets the buffer without tearing
 * down the track.
 */
@RunWith(RobolectricTestRunner::class)
class PresentationSchedulerBehaviorTest {

    private val scheduler = PresentationScheduler()

    @After
    fun tearDown() {
        scheduler.stop()
    }

    private fun chunk(targetNanos: Long, sequenceNumber: Long): AudioChunk =
        AudioChunk(
            hostTimestampNanos = targetNanos,
            sequenceNumber = sequenceNumber,
            pcmPayload = ByteArray(3_840),
        )

    /**
     * A chunk's guest-playback target is hostTimestamp + offset + lookaheadMs,
     * so a host timestamp `lookaheadMs - 5ms` in the past schedules the chunk
     * 5ms in the future — within the 20ms grace either way, never dropped.
     */
    private fun dueNowTimestamp(): Long = System.nanoTime() - 245_000_000L

    private fun lateTimestamp(lateMs: Long): Long = System.nanoTime() - (250_000_000L + lateMs * 1_000_000L)

    private fun awaitChunkCount(timeoutMs: Long = 1_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
    }

    @Test
    fun dueChunk_isWrittenToTheAudioTrack() {
        assertTrue(scheduler.openAudioTrack())
        // Buffer seeded BEFORE the thread starts: the thread parks precisely on
        // the chunk's target instead of racing a 20ms park cycle, so the write
        // path is deterministic. Target is 150ms out (lookahead 250ms − 100ms).
        scheduler.onChunkReceived(chunk(System.nanoTime() - 100_000_000L, 1L))
        scheduler.start()

        awaitChunkCount { scheduler.chunksPlayed > 0 }
        assertEquals(1L, scheduler.chunksPlayed)
        assertEquals(0L, scheduler.chunksDropped)
    }

    @Test
    fun chunkLateByMoreThanOneFrame_isDropped() {
        assertTrue(scheduler.openAudioTrack())
        scheduler.start()

        // 30ms past its target — more than one frame duration late (BE §7:
        // "passed by more than one frame duration (20ms) are dropped").
        scheduler.onChunkReceived(chunk(lateTimestamp(30), 1L))

        awaitChunkCount { scheduler.chunksDropped > 0 }
        assertEquals(1L, scheduler.chunksDropped)
        assertEquals(0L, scheduler.chunksPlayed)
    }

    @Test
    fun futureChunk_waitsUntilItsTargetTime() {
        assertTrue(scheduler.openAudioTrack())
        scheduler.start()

        val targetNanos = System.nanoTime() + 500_000_000L
        scheduler.onChunkReceived(chunk(targetNanos, 1L))

        // 80ms in: the chunk is still buffered (target is ~500ms out), not yet written.
        Thread.sleep(80)
        assertEquals(0L, scheduler.chunksPlayed)
        assertEquals(1, scheduler.bufferSize)

        // Well past the target: it was written.
        awaitChunkCount(1_500) { scheduler.chunksPlayed > 0 }
        assertEquals(1L, scheduler.chunksPlayed)
    }

    @Test
    fun flush_clearsBufferAndResetsCountersButKeepsTrackAlive() {
        assertTrue(scheduler.openAudioTrack())
        scheduler.start()

        val farFuture1 = System.nanoTime() + 10_000_000_000L
        val farFuture2 = System.nanoTime() + 11_000_000_000L
        scheduler.onChunkReceived(chunk(farFuture1, 1L))
        scheduler.onChunkReceived(chunk(farFuture2, 2L))
        assertEquals(2, scheduler.bufferSize)

        // BE §7 — flush-and-reseed: stale chunks are discarded, counters reset,
        // but the scheduler keeps running and the AudioTrack stays initialized.
        scheduler.flush()
        assertEquals(0, scheduler.bufferSize)
        assertEquals(0L, scheduler.chunksPlayed)
        assertTrue(scheduler.isRunning)
        assertTrue(scheduler.isAudioTrackReady)
        assertTrue(scheduler.getAudioTrack()?.playState == android.media.AudioTrack.PLAYSTATE_PLAYING)
    }

    @Test
    fun seedFromNow_clearsTheBuffer() {
        assertTrue(scheduler.openAudioTrack())
        scheduler.start()

        scheduler.onChunkReceived(chunk(System.nanoTime() + 10_000_000_000L, 1L))
        assertEquals(1, scheduler.bufferSize)

        // The single seeding path (BE §7) — clears the ring buffer.
        scheduler.seedFromNow()
        assertEquals(0, scheduler.bufferSize)
    }
}

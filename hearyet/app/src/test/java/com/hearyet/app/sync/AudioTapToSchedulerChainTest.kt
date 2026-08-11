package com.hearyet.app.sync

import com.hearyet.app.feature.player.sync.AudioChunk
import com.hearyet.app.feature.player.sync.SharedAudioRenderer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BE §4/§6 — the full host-side chain, wired exactly as production connects it:
 *
 * SharedAudioRenderer PCM tap → GuestOutboundQueue (bounded, drop-oldest) →
 * AudioChunk.writeFramedChunk (continuous STREAM) → PipedInputStream →
 * AudioChunk.readFramedChunk → PresentationScheduler.onChunkReceived.
 *
 * Every hop is the real production class; only the Nearby Connections API itself
 * (and the scheduler's AudioTrack) are absent, represented by the pipe pair.
 */
@RunWith(RobolectricTestRunner::class)
class AudioTapToSchedulerChainTest {

    @Test
    fun pcmTap_toOutboundQueue_toFramedStream_toScheduler_preservesFramesInOrder() {
        val queue = GuestOutboundQueue("ep-guest")

        // 1. Tap: SharedAudioRenderer chunks 3 full 20ms frames (3840B each @48k stereo).
        val renderer = SharedAudioRenderer().apply {
            onAudioChunk = { queue.enqueue(it) }
        }
        val pcm = ByteArray(3_840 * 3) { (it % 251).toByte() }
        renderer.queueInput(ByteBuffer.wrap(pcm))
        assertEquals(3, queue.size)

        // 2. Transport leg: the sender thread writes framed records into the pipe;
        //    the receiver (guest-side reader thread) parses them back out.
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 65_536)
        val received = mutableListOf<AudioChunk>()
        Thread {
            while (true) {
                val chunk = queue.poll() ?: break
                AudioChunk.writeFramedChunk(pipedOut, chunk)
            }
            pipedOut.close()
        }.apply {
            isDaemon = true
            start()
        }
        while (true) {
            val chunk = AudioChunk.readFramedChunk(pipedIn) ?: break
            received.add(chunk)
        }

        // 3. Scheduler: every received frame lands in the presentation ring buffer.
        val scheduler = PresentationScheduler()
        received.forEach { scheduler.onChunkReceived(it) }

        assertEquals(3, received.size)
        assertEquals(3, scheduler.bufferSize)
        assertEquals(3L, scheduler.chunksReceived)

        // Sequence continuity and payload integrity across the whole chain.
        assertEquals(listOf(0L, 1L, 2L), received.map { it.sequenceNumber })
        assertArrayEquals(pcm.copyOfRange(0, 3_840), received[0].pcmPayload)
        assertArrayEquals(pcm.copyOfRange(3_840, 7_680), received[1].pcmPayload)
        assertArrayEquals(pcm.copyOfRange(7_680, 11_520), received[2].pcmPayload)
    }
}

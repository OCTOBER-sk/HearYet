package com.hearyet.app.feature.player.sync

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAudioRendererTest {

    private fun newRenderer(): SharedAudioRenderer = SharedAudioRenderer().apply {
        onAudioChunk = { }
    }

    private fun start(renderer: SharedAudioRenderer): SharedAudioRenderer {
        renderer.configure(
            AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT),
        )
        return renderer
    }

    /** Drain the pass-through output buffer and return its bytes. */
    private fun drain(renderer: SharedAudioRenderer): ByteArray {
        val output = renderer.output
        val data = ByteArray(output.remaining())
        output.get(data)
        return data
    }

    @Test
    fun queueInput_misalignedBuffers_passThroughPreservesEveryByte() {
        val renderer = start(newRenderer())

        // Deliberately non-multiples of a 20ms frame (3840 bytes @48kHz stereo
        // 16-bit): each buffer's full window must survive the pass-through.
        val first = ByteArray(1_000) { (it % 251).toByte() }
        val second = ByteArray(2_500) { (it % 253).toByte() }
        val third = ByteArray(777) { (it % 257).toByte() }

        renderer.queueInput(ByteBuffer.wrap(first))
        assertArrayEquals(first, drain(renderer))
        renderer.queueInput(ByteBuffer.wrap(second))
        assertArrayEquals(second, drain(renderer))
        renderer.queueInput(ByteBuffer.wrap(third))
        assertArrayEquals(third, drain(renderer))
    }

    @Test
    fun queueInput_consumesInputSoThePipelineNeverRequeues() {
        val renderer = start(newRenderer())

        val input = ByteBuffer.wrap(ByteArray(2_000) { 0x2A })
        renderer.queueInput(input)

        // The input must be fully consumed (position == limit) — otherwise the
        // sink re-queues the same buffer and duplicates audio.
        assertEquals(input.limit(), input.position())
    }

    @Test
    fun queueInput_emitsFramesOnEveryComplete20msBoundary() {
        val renderer = start(newRenderer())
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        // Frame = 20ms @ 48kHz stereo 16-bit = 3840 bytes.
        // 2 full frames (7680 bytes) + 100 bytes of overflow.
        renderer.queueInput(ByteBuffer.wrap(ByteArray(7_780)))

        assertEquals(2, chunks.size)
        assertEquals(3_840, chunks[0].pcmPayload.size)
        assertEquals(3_840, chunks[1].pcmPayload.size)
        assertEquals(0L, chunks[0].sequenceNumber)
        assertEquals(1L, chunks[1].sequenceNumber)
    }

    @Test
    fun queueInput_bufferReuseAcrossCycles_preservesEachWindow() {
        val renderer = start(newRenderer())

        // First cycle.
        val first = ByteArray(2_000) { (it % 251).toByte() }
        val input = ByteBuffer.wrap(first)
        renderer.queueInput(input)
        assertArrayEquals(first, drain(renderer))

        // Second cycle reusing the SAME buffer instance (the pipeline's
        // zero-copy reuse) with a smaller window and non-zero position.
        input.clear()
        val second = ByteArray(1_500) { (it % 253).toByte() }
        input.position(5)
        input.put(second)
        input.position(5)
        input.limit(1_505)

        renderer.queueInput(input)
        assertArrayEquals(second, drain(renderer))
        assertEquals(1_505, input.position())
    }

    @Test
    fun queueInput_withNoListener_doesNotChunkAndStillPassesThrough() {
        val renderer = start(SharedAudioRenderer())

        val input = ByteArray(2_000) { 0x2A }
        renderer.queueInput(ByteBuffer.wrap(input))

        assertArrayEquals(input, drain(renderer))
    }

    @Test
    fun queueInput_overflowCarriesAcrossCalls_completesFrameByteExactly() {
        val renderer = start(newRenderer())
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        // 4000 bytes = 3840 (one full frame) + 160 overflow.
        val first = ByteArray(4_000) { (it % 251).toByte() }
        renderer.queueInput(ByteBuffer.wrap(first))
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].sequenceNumber)

        // 3680 bytes + the retained 160 = exactly one more frame (no stray remainder).
        val second = ByteArray(3_680) { (it % 253).toByte() }
        renderer.queueInput(ByteBuffer.wrap(second))

        assertEquals(2, chunks.size)
        assertEquals(1L, chunks[1].sequenceNumber)
        assertEquals(3_840, chunks[1].pcmPayload.size)
        // The carried overflow bytes lead the payload, then the new bytes follow.
        assertArrayEquals(
            first.copyOfRange(3_840, 4_000) + second,
            chunks[1].pcmPayload,
        )
    }

    @Test
    fun queueInput_overflowDoesNotAccumulateAcrossAlignedFrames() {
        val renderer = start(newRenderer())
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        // Frame-aligned input must leave zero overflow behind.
        renderer.queueInput(ByteBuffer.wrap(ByteArray(3_840 * 3)))
        assertEquals(3, chunks.size)

        // A small remainder then starts a fresh accumulation from zero.
        renderer.queueInput(ByteBuffer.wrap(ByteArray(500)))
        assertEquals(3, chunks.size)
    }

    @Test
    fun queueInput_multiframeBuffer_stampsOneFrameDurationPerFrame() {
        // M-1 — every frame emitted from a single input buffer must carry its own
        // capture time: timestamp = tapTime + n·frameDuration (n = frame index in
        // the buffer). A burst of near-identical timestamps would target all frames
        // at the same instant on the guest scheduler (drop/stutter).
        val renderer = start(newRenderer())
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        // 3 full frames @ 20ms = 11520 bytes.
        renderer.queueInput(ByteBuffer.wrap(ByteArray(3_840 * 3)))

        assertEquals(3, chunks.size)
        val frameDurationNanos = SharedAudioRenderer.FRAME_DURATION_MS * 1_000_000L
        // Exact spacing between consecutive frames, in emission order.
        assertEquals(frameDurationNanos, chunks[1].hostTimestampNanos - chunks[0].hostTimestampNanos)
        assertEquals(frameDurationNanos, chunks[2].hostTimestampNanos - chunks[1].hostTimestampNanos)
        // The first frame is stamped at the buffer's tap time — never in the future.
        assertTrue(chunks[0].hostTimestampNanos <= System.nanoTime())
    }

    @Test
    fun onFlush_clearsRetainedOverflow() {
        val renderer = start(newRenderer())
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        // 1000 bytes retained as overflow (no full frame).
        renderer.queueInput(ByteBuffer.wrap(ByteArray(1_000)))
        assertEquals(0, chunks.size)

        renderer.flush()

        // Without the flush, 2840 more bytes would complete a frame; after the
        // flush the retained overflow is gone, so no chunk may be emitted.
        renderer.queueInput(ByteBuffer.wrap(ByteArray(2_840)))
        assertEquals(0, chunks.size)
    }

    @Test
    fun frameSizeBytes_usesActualEncodingForSampleWidth() {
        // FIX 3 — frame size must come from the actual encoding, not a hardcoded
        // 16-bit width: float PCM (4 bytes/sample) doubles the frame size.
        assertEquals(3_840, SharedAudioRenderer.frameSizeBytes(48_000, 2, C.ENCODING_PCM_16BIT))
        assertEquals(7_680, SharedAudioRenderer.frameSizeBytes(48_000, 2, C.ENCODING_PCM_FLOAT))
        assertEquals(1_764, SharedAudioRenderer.frameSizeBytes(44_100, 1, C.ENCODING_PCM_16BIT))
        assertEquals(960, SharedAudioRenderer.frameSizeBytes(48_000, 1, C.ENCODING_PCM_8BIT))
    }

    @Test
    fun queueInput_floatEncoding_emitsFloatSizedFrames() {
        // FIX 3 — a float-output renderer must chunk by the float frame size;
        // the old 16-bit hardcode would emit half-frames of garbage.
        val renderer = newRenderer()
        renderer.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        renderer.queueInput(ByteBuffer.wrap(ByteArray(7_680)))

        assertEquals(1, chunks.size)
        assertEquals(7_680, chunks[0].pcmPayload.size)
    }

    @Test
    fun queueInput_emitsChunksCarryingCaptureFormat() {
        // FIX 3 — the chunk must carry the host's capture format so the guest can
        // build a matching AudioTrack (44.1kHz/mono here).
        val renderer = newRenderer()
        renderer.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        val chunks = mutableListOf<AudioChunk>()
        renderer.onAudioChunk = { chunks.add(it) }

        renderer.queueInput(ByteBuffer.wrap(ByteArray(1_764)))

        assertEquals(1, chunks.size)
        assertEquals(44_100, chunks[0].sampleRateHz)
        assertEquals(1, chunks[0].channelCount)
        assertEquals(1_764, chunks[0].pcmPayload.size)
    }
}

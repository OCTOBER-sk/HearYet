package com.hearyet.app.feature.player.sync

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * A transparent [AudioProcessor] that taps decoded PCM from the end of the
 * host's audio sink chain before it reaches [android.media.AudioTrack],
 * producing [AudioChunk]s for transport to guests.
 *
 * Pass-through strategy — reference, zero-copy:
 *
 * The processor NEVER copies or mutates the audio bytes. [queueInput] merely
 * remembers the queued buffer and exposes it back from [getOutput] with its
 * original position/limit window restored. The input is then consumed
 * (position advanced to limit) so the Media3 pipeline never re-queues the same
 * data. This makes solo (Watch) playback bit-for-bit identical to a player
 * with no processor at all.
 *
 * Chunking reads a byte snapshot inside [queueInput] without touching the
 * buffer's contents; the tap listener is a no-op unless a host session is
 * active.
 */
class SharedAudioRenderer : BaseAudioProcessor() {

    // ── Public callback ─────────────────────────────────────────────
    /** Called on the audio sink thread for each complete 20 ms PCM frame.
     *  Set before [onConfigure] is called (typically at construction time). */
    var onAudioChunk: ((AudioChunk) -> Unit)? = null

    // ── Frame size ──────────────────────────────────────────────────
    private var channelCount: Int = 2
    private var sampleRateHz: Int = 48000
    private var sequenceNumber: Long = 0L

    /** Overflow from a previous [queueInput] that didn't align to a
     *  frame boundary. Always < one full frame in size. */
    private var overflow: ByteArray = ByteArray(0)

    // ── Reference pass-through state ────────────────────────────────
    /** The queued input, to be handed back as output once. */
    private var pendingOutput: ByteBuffer? = null
    /** The input's original position, restored on [getOutput]. */
    private var pendingPosition: Int = 0

    // ── AudioProcessor implementation ───────────────────────────────

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // Pass-through: output format identical to input format.
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val listener = onAudioChunk
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        val pos = inputBuffer.position()
        val limit = inputBuffer.limit()
        val frameBytes = frameSizeBytes(sampleRateHz, channelCount)

        // Chunking snapshot — pure reads from the input window, done before
        // the input is consumed below.
        if (listener != null && frameBytes > 0) {
            val combined = if (overflow.isNotEmpty()) {
                val total = overflow.size + inputSize
                ByteArray(total).also { arr ->
                    System.arraycopy(overflow, 0, arr, 0, overflow.size)
                    inputBuffer.get(arr, overflow.size, inputSize)
                }
            } else {
                ByteArray(inputSize).also { arr ->
                    inputBuffer.get(arr)
                }
            }

            var offset = 0
            while (offset + frameBytes <= combined.size) {
                listener(
                    AudioChunk(
                        hostTimestampNanos = System.nanoTime(),
                        sequenceNumber = sequenceNumber++,
                        pcmPayload = combined.copyOfRange(offset, offset + frameBytes),
                    ),
                )
                offset += frameBytes
            }
            overflow = if (offset < combined.size) {
                combined.copyOfRange(offset, combined.size)
            } else {
                ByteArray(0)
            }
        }

        // Reference pass-through: hand the queued buffer back as output with
        // its data window restored, and consume the input so the pipeline
        // never re-queues the same data (which would duplicate audio).
        pendingOutput = inputBuffer
        pendingPosition = pos
        inputBuffer.position(limit)
    }

    override fun getOutput(): ByteBuffer {
        val out = pendingOutput
        pendingOutput = null
        if (out != null) {
            out.position(pendingPosition)
            return out
        }
        return EMPTY_OUTPUT
    }

    override fun onQueueEndOfStream() {
        overflow = ByteArray(0)
        pendingOutput = null
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        overflow = ByteArray(0)
        pendingOutput = null
    }

    override fun onReset() {
        overflow = ByteArray(0)
        sequenceNumber = 0L
        sampleRateHz = 48000
        channelCount = 2
        pendingOutput = null
    }

    companion object {
        /** Duration of each emitted frame in milliseconds. */
        const val FRAME_DURATION_MS: Int = 20

        /** Bytes per 20 ms frame for 16-bit interleaved PCM. */
        fun frameSizeBytes(sampleRateHz: Int, channelCount: Int): Int =
            (sampleRateHz * channelCount * 2L * FRAME_DURATION_MS / 1000L).toInt()

        private val EMPTY_OUTPUT: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}

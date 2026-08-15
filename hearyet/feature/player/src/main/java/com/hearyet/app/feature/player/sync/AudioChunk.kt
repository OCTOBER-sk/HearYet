package com.hearyet.app.feature.player.sync

import android.util.Log
import java.nio.ByteBuffer

data class AudioChunk(
    /** Host capture time from System.nanoTime(). */
    val hostTimestampNanos: Long,
    val sequenceNumber: Long,
    /** Raw PCM, interleaved, at [sampleRateHz]/[channelCount] (was opusPayload in original plan). */
    val pcmPayload: ByteArray,
    /**
     * FIX 3 — host capture sample rate (Hz). Carried on the wire so the guest can
     * build its AudioTrack to match (a hardcoded 48kHz track plays 44.1kHz media
     * ~8.8% fast and double-speed mono).
     */
    val sampleRateHz: Int = 48000,
    /** FIX 3 — host capture channel count (1 = mono, 2 = stereo). */
    val channelCount: Int = 2,
) {
    companion object {
        private const val TAG = "AudioChunk"

        /** Header size: 8B timestamp + 8B sequence + 4B sampleRate + 4B channelCount = 24 bytes. */
        const val HEADER_SIZE_BYTES: Int = 24

        /**
         * Serialize this chunk for STREAM transport with a binary header so the
         * guest can recover [hostTimestampNanos], [sequenceNumber], [sampleRateHz]
         * and [channelCount] for the scheduling formula and AudioTrack format
         * (BE §6, §7; FIX 3).
         *
         * Format: [timestamp: 8B BE][sequenceNumber: 8B BE][sampleRate: 4B BE][channelCount: 4B BE][pcmPayload: N bytes]
         */
        fun encodeToHeaderBytes(
            hostTimestampNanos: Long,
            sequenceNumber: Long,
            sampleRateHz: Int,
            channelCount: Int,
            pcmPayload: ByteArray,
        ): ByteArray {
            val buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + pcmPayload.size)
            buffer.putLong(hostTimestampNanos)
            buffer.putLong(sequenceNumber)
            buffer.putInt(sampleRateHz)
            buffer.putInt(channelCount)
            buffer.put(pcmPayload)
            return buffer.array()
        }

        /**
         * Parse a header-prepended chunk received on the STREAM channel.
         * Returns null if the data is too short to contain a valid header.
         */
        fun decodeFromHeaderBytes(data: ByteArray): AudioChunk? {
            if (data.size < HEADER_SIZE_BYTES) return null
            val buffer = ByteBuffer.wrap(data)
            val hostTimestampNanos = buffer.getLong()
            val sequenceNumber = buffer.getLong()
            val sampleRateHz = buffer.getInt()
            val channelCount = buffer.getInt()
            val pcmPayload = ByteArray(buffer.remaining())
            buffer.get(pcmPayload)
            return AudioChunk(
                hostTimestampNanos = hostTimestampNanos,
                sequenceNumber = sequenceNumber,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                pcmPayload = pcmPayload,
            )
        }

        /** Maximum body length for a framed STREAM record (sanity bound on read). */
        private const val MAX_FRAME_BODY_BYTES = 1_000_000

        /** Magic byte identifying a HearYet STREAM frame (0x48 = 'H'). */
        private const val FRAME_MAGIC: Byte = 0x48

        /**
         * Framing format version — bump whenever the wire layout below changes.
         * 0x02: header grew from 16 to 24 bytes (added sampleRate + channelCount).
         */
        private const val FRAME_VERSION: Byte = 0x02

        /** Total prefix length: 1B magic + 1B version + 4B record length. */
        private const val FRAME_PREFIX_BYTES = 6

        /**
         * Continuous-stream framing (BE §4 — "the continuous PCM audio feed"): each
         * record on the wire is
         * [1B magic][1B version][4B big-endian Int: record length][24-byte header][PCM payload].
         * The length prefix lets the receiver find record boundaries in an
         * otherwise-unbroken byte stream; magic+version make a mismatched build fail
         * loudly (dropped + logged) instead of silently producing garbage audio.
         */
        fun writeFramedChunk(out: java.io.OutputStream, chunk: AudioChunk) {
            val body = encodeToHeaderBytes(
                chunk.hostTimestampNanos,
                chunk.sequenceNumber,
                chunk.sampleRateHz,
                chunk.channelCount,
                chunk.pcmPayload,
            )
            val prefix = java.nio.ByteBuffer.allocate(FRAME_PREFIX_BYTES)
                .put(FRAME_MAGIC)
                .put(FRAME_VERSION)
                .putInt(body.size)
                .array()
            out.write(prefix)
            out.write(body)
        }

        /**
         * Read one framed chunk from [input]. Returns null when the stream closes or
         * EOF is hit mid-record (host disconnected), or when the record is malformed
         * (bad magic/version, non-positive or oversized length) — a corrupted stream.
         */
        fun readFramedChunk(input: java.io.InputStream): AudioChunk? {
            val prefix = input.readNBytesCompat(FRAME_PREFIX_BYTES) ?: return null
            if (prefix[0] != FRAME_MAGIC || prefix[1] != FRAME_VERSION) {
                Log.w(TAG, "readFramedChunk: incompatible STREAM framing (magic=${prefix[0]} version=${prefix[1]}) — different app builds?")
                return null
            }
            val length = java.nio.ByteBuffer.wrap(prefix, 2, 4).int
            if (length <= 0 || length > MAX_FRAME_BODY_BYTES) return null
            val body = input.readNBytesCompat(length) ?: return null
            return decodeFromHeaderBytes(body)
        }

        // InputStream.readNBytes(int) is API 33+; this project's SDK floor is much
        // lower (BE §0) — hand-roll the loop instead of assuming the platform method.
        private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray? {
            val buf = ByteArray(n)
            var off = 0
            while (off < n) {
                val read = this.read(buf, off, n - off)
                if (read == -1) return null
                off += read
            }
            return buf
        }
    }
}

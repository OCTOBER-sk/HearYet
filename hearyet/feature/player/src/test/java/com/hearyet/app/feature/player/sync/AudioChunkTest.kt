package com.hearyet.app.feature.player.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BE §4/§6 — continuous-STREAM framing: each record on the wire is
 * [4-byte length][16-byte header][PCM payload]; the length prefix lets the
 * receiver find record boundaries in an otherwise-unbroken byte stream.
 */
class AudioChunkTest {

    private fun chunk(seq: Long, payloadSize: Int = 3_840): AudioChunk = AudioChunk(
        hostTimestampNanos = 1_000_000_000L + seq,
        sequenceNumber = seq,
        sampleRateHz = 44_100,
        channelCount = 1,
        pcmPayload = ByteArray(payloadSize) { (it % 251).toByte() },
    )

    @Test
    fun writeFramedChunk_thenReadFramedChunk_roundTripsExactly() {
        val out = ByteArrayOutputStream()
        val original = chunk(seq = 7)
        AudioChunk.writeFramedChunk(out, original)
        val bytes = out.toByteArray()

        // 1B magic + 1B version + 4B length + 24B header + 3840B payload
        assertEquals(6 + 24 + 3_840, bytes.size)

        val decoded = AudioChunk.readFramedChunk(ByteArrayInputStream(bytes))!!
        assertArrayEquals(original.pcmPayload, decoded.pcmPayload)
        assertEquals(original.hostTimestampNanos, decoded.hostTimestampNanos)
        assertEquals(original.sequenceNumber, decoded.sequenceNumber)
        // FIX 3 — the wire must carry the capture format so the guest builds a
        // matching AudioTrack (44.1kHz/mono here).
        assertEquals(44_100, decoded.sampleRateHz)
        assertEquals(1, decoded.channelCount)
    }

    @Test
    fun writeFramedChunk_44_1kHzStereoFormat_roundTripsExactly() {
        val out = ByteArrayOutputStream()
        val original = AudioChunk(
            hostTimestampNanos = 42L,
            sequenceNumber = 9L,
            sampleRateHz = 48_000,
            channelCount = 2,
            pcmPayload = ByteArray(2_000) { 0x11 },
        )
        AudioChunk.writeFramedChunk(out, original)

        val decoded = AudioChunk.readFramedChunk(ByteArrayInputStream(out.toByteArray()))!!
        assertEquals(42L, decoded.hostTimestampNanos)
        assertEquals(9L, decoded.sequenceNumber)
        assertEquals(48_000, decoded.sampleRateHz)
        assertEquals(2, decoded.channelCount)
        assertArrayEquals(original.pcmPayload, decoded.pcmPayload)
    }

    @Test
    fun readFramedChunk_multipleRecordsInOneStream_areSplitOnBoundaries() {
        val out = ByteArrayOutputStream()
        AudioChunk.writeFramedChunk(out, chunk(seq = 0))
        AudioChunk.writeFramedChunk(out, chunk(seq = 1))
        val input = ByteArrayInputStream(out.toByteArray())

        val first = AudioChunk.readFramedChunk(input)!!
        val second = AudioChunk.readFramedChunk(input)!!
        assertEquals(0L, first.sequenceNumber)
        assertEquals(1L, second.sequenceNumber)

        // Stream fully consumed -> EOF on the next read.
        assertNull(AudioChunk.readFramedChunk(input))
    }

    @Test
    fun readFramedChunk_partialRecordAtEndOfStream_returnsNull() {
        val out = ByteArrayOutputStream()
        AudioChunk.writeFramedChunk(out, chunk(seq = 0))
        val bytes = out.toByteArray()

        // Truncate mid-body (6 prefix + 24 header + 100 of the 3840 payload bytes).
        val truncated = bytes.copyOf(6 + 24 + 100)
        assertNull(AudioChunk.readFramedChunk(ByteArrayInputStream(truncated)))
    }

    @Test
    fun readFramedChunk_badMagicOrVersion_returnsNull() {
        // Magic mismatch (0x7F instead of 'H').
        val badMagic = byteArrayOf(0x7F.toByte(), 0x01, 0x00, 0x00, 0x00, 0x01)
        assertNull(AudioChunk.readFramedChunk(ByteArrayInputStream(badMagic)))

        // Version mismatch (0x03 — a build newer than this one).
        val badVersion = byteArrayOf(0x48, 0x03, 0x00, 0x00, 0x00, 0x01)
        assertNull(AudioChunk.readFramedChunk(ByteArrayInputStream(badVersion)))
    }

    @Test
    fun readFramedChunk_garbageLengthPrefix_returnsNull() {
        val huge = byteArrayOf(0x48, 0x01, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertNull(AudioChunk.readFramedChunk(ByteArrayInputStream(huge)))

        val negative = byteArrayOf(0x48, 0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertNull(AudioChunk.readFramedChunk(ByteArrayInputStream(negative)))
    }

    @Test
    fun framing_overPipedStream_blocksUntilRecordWritten() {
        // The transport's continuous stream: sender writes framed records over a
        // live pipe; the receiver's read blocks until the record arrives.
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 65_536)
        val original = chunk(seq = 3)

        Thread {
            AudioChunk.writeFramedChunk(pipedOut, original)
            pipedOut.close()
        }.apply {
            isDaemon = true
            start()
        }

        val decoded = AudioChunk.readFramedChunk(pipedIn)!!
        assertArrayEquals(original.pcmPayload, decoded.pcmPayload)
        assertEquals(3L, decoded.sequenceNumber)

        // Stream closed after the record -> EOF.
        assertNull(AudioChunk.readFramedChunk(pipedIn))
    }
}

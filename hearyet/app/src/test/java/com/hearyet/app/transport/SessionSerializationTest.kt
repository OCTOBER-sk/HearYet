package com.hearyet.app.transport

import com.hearyet.app.core.model.SyncHealth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionSerializationTest {

    private val json = Json

    @Test
    fun sessionPayload_roundTripsThroughCodec() {
        val payload = SessionPayload(
            sessionId = "session-id",
            sessionCode = "7H3K9P",
            hostEndpointName = "hearyet-7H3K9P",
            hostDisplayName = "Pixel 8",
        )

        val decoded = SessionPayloadCodec.decode(SessionPayloadCodec.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun decode_returnsNullForMalformedPayload() {
        assertNull(SessionPayloadCodec.decode("not-a-session-payload"))
    }

    @Test
    fun generateSessionCode_usesSixCharacterCrockfordBase32() {
        repeat(100) {
            val code = SessionPayloadCodec.generateSessionCode()

            assertEquals(6, code.length)
            assertTrue(code.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
            assertTrue(code.none { it in "ILOU" })
        }
    }

    @Test
    fun controlMessages_roundTripThroughPolymorphicJson() {
        val messages = listOf<ControlMessage>(
            ControlMessage.ClockSyncRequest(t0 = 1L),
            ControlMessage.ClockSyncResponse(t0 = 1L, t1 = 2L, t2 = 3L),
            ControlMessage.PlaybackState(
                isPlaying = true,
                positionMs = 4L,
                sharedClockTimestampNanos = 5L,
            ),
            ControlMessage.GuestJoined(endpointId = "endpoint", displayName = "Guest"),
            ControlMessage.SessionEnded,
            ControlMessage.DriftReport(driftMs = 6.5),
            ControlMessage.Heartbeat(hostTimestampNanos = 7L),
            ControlMessage.SeekTo(positionMs = 8L, sharedClockTimestampNanos = 9L),
            ControlMessage.MediaChanged(mediaTitle = "Track", sharedClockTimestampNanos = 10L),
            ControlMessage.AudioTrackChanged(trackId = "audio", sharedClockTimestampNanos = 11L),
            ControlMessage.RejoinRequest(previousEndpointId = "old", displayName = "Guest"),
            ControlMessage.SessionHandshake(sessionId = "session-id"),
            ControlMessage.SessionHandshakeAck(sessionId = "session-id"),
        )

        messages.forEach { message ->
            val encoded = json.encodeToString<ControlMessage>(message)
            val decoded = json.decodeFromString<ControlMessage>(encoded)

            assertNotNull(decoded)
            assertEquals(message, decoded)
        }
    }

    @Test
    fun syncHealth_hasOnlySpecifiedValues() {
        assertEquals(setOf(SyncHealth.GOOD, SyncHealth.DEGRADED, SyncHealth.POOR), SyncHealth.entries.toSet())
    }
}

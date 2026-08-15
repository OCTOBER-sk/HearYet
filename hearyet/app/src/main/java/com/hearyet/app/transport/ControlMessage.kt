package com.hearyet.app.transport

import kotlinx.serialization.Serializable

@Serializable
sealed class ControlMessage {
    @Serializable
    data class ClockSyncRequest(
        /** Captured from System.nanoTime() on the requesting device. */
        val t0: Long,
    ) : ControlMessage()

    @Serializable
    data class ClockSyncResponse(
        /** Original System.nanoTime() value from the request. */
        val t0: Long,
        /** System.nanoTime() when the request was received. */
        val t1: Long,
        /** System.nanoTime() when the response was sent. */
        val t2: Long,
    ) : ControlMessage()

    @Serializable
    data class PlaybackState(
        val isPlaying: Boolean,
        val positionMs: Long,
        /** Shared timeline anchor captured from System.nanoTime(). */
        val sharedClockTimestampNanos: Long,
    ) : ControlMessage()

    @Serializable
    data class GuestJoined(
        val endpointId: String,
        val displayName: String,
    ) : ControlMessage()

    @Serializable
    data object SessionEnded : ControlMessage()

    /**
     * FIX 2a (R1) — Host → guest: the host's outbound queue for this guest backed
     * up (chronically full, so it was dropping the oldest → the guest would receive
     * ~4s-stale chunks). The guest should flush its ring buffer and re-seed from
     * "now" instead of staying permanently behind in the drop-cascade.
     */
    @Serializable
    data object ResyncAudio : ControlMessage()

    @Serializable
    data class DriftReport(val driftMs: Double) : ControlMessage()

    @Serializable
    data class Heartbeat(
        /** Captured from System.nanoTime() on the host. */
        val hostTimestampNanos: Long,
    ) : ControlMessage()

    @Serializable
    data class SeekTo(
        val positionMs: Long,
        /** Shared timeline anchor captured from System.nanoTime(). */
        val sharedClockTimestampNanos: Long,
    ) : ControlMessage()

    @Serializable
    data class MediaChanged(
        val mediaTitle: String,
        /** Shared timeline anchor captured from System.nanoTime(). */
        val sharedClockTimestampNanos: Long,
    ) : ControlMessage()

    @Serializable
    data class AudioTrackChanged(
        val trackId: String,
        /** Shared timeline anchor captured from System.nanoTime(). */
        val sharedClockTimestampNanos: Long,
    ) : ControlMessage()

    @Serializable
    data class RejoinRequest(
        val previousEndpointId: String,
        val displayName: String,
    ) : ControlMessage()

    @Serializable
    data class SessionHandshake(
        /** The sessionId the guest expects to be connected to (BE §4 discovery rule). */
        val sessionId: String,
    ) : ControlMessage()

    @Serializable
    data class SessionHandshakeAck(
        /** The host's actual sessionId — the guest compares it against the QR payload's. */
        val sessionId: String,
    ) : ControlMessage()
}

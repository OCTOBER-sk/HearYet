package com.hearyet.app.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-module interface for observing an active session from the UI layer.
 *
 * [com.hearyet.app.sync.SessionCoordinator] in the `:app` module implements this.
 * `:feature:player` reads it from the Application without depending on `:app`.
 *
 * C11 — In-session Host/Guest UI (FE §9.6).
 */
interface SessionHandle {
    val sessionState: StateFlow<SessionState>
    val hostGuestCount: StateFlow<Int>
    val sessionCode: String?

    /** True if this device is the session Host. */
    val isHost: Boolean

    /** FE §9.13 — the Host's display name as known to a Guest (null for Hosts). */
    val hostDisplayName: String?

    /** BE §11 — the encoded QR payload string (for rendering the session QR). */
    val qrPayload: String?

    /** BE §8 — live per-guest info for the Host's guest list UI (names, drift, SyncHealth). */
    val hostGuests: StateFlow<List<GuestInfo>>

    fun endSession()

    fun leaveSession()

    /** BE §7 — Host seek: broadcast SeekTo to all guests. */
    fun onHostSeeked(positionMs: Long)

    /** BE §7 — Host pause/resume: broadcast PlaybackState to all guests. */
    fun onHostPlayPause(isPlaying: Boolean, positionMs: Long)

    /** BE §7 — Host media change: broadcast MediaChanged then PlaybackState. */
    fun onHostMediaChanged(mediaTitle: String)

    /**
     * BE §7/§12 — Host manually switched the active audio track. The PCM feed the tap
     * captures changes, so this must be broadcast so guests flush-and-reseed.
     *
     * @param trackId identifier for the newly selected audio track.
     */
    fun onHostAudioTrackChanged(trackId: String)

    /**
     * Host-only: a raw PCM audio frame was tapped by [SharedAudioRenderer]
     * for transport to all connected guests. The coordinator is responsible
     * for distributing this frame to each guest's outbound queue.
     *
     * @param hostTimestampNanos Host capture time from System.nanoTime().
     * @param sequenceNumber Monotonic frame counter for gap detection.
     * @param pcmPayload Raw 16-bit PCM, 48kHz, interleaved stereo.
     */
    fun onHostAudioChunk(hostTimestampNanos: Long, sequenceNumber: Long, pcmPayload: ByteArray)

    /**
     * Host-only: a session has become active (media is playing and guests may connect).
     * Called by [PlayerService] to notify the coordinator that screen-off handling
     * (DummySurface) should be activated.
     */
    fun onSessionActive()

    /**
     * Host-only: the session has ended. Called to deactivate screen-off handling.
     */
    fun onSessionEnded()
}

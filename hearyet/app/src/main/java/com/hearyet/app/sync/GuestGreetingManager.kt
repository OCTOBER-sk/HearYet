package com.hearyet.app.sync

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hearyet.app.HearYetApplication
import com.hearyet.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Plays a short confirmation chime on the Guest device when it first begins
 * receiving synced audio in a session — heard only by that Guest, never by
 * the Host, never more than once per guest per session.
 *
 * ## Lifecycle (Section 2.3 of the directive)
 * - [preload] — when Guest enters Discovering (not at app launch)
 * - [maybeGreet] — on first [SessionState.Playing] transition this session
 * - [onSessionEnded] — clears per-session greeted set
 * - [release] — foreground-service teardown (frees SoundPool)
 *
 * ## Anti-spam (Section 4)
 * - Tracks greeted guest identities per session via [greetedThisSession].
 * - Uses the **rejoin identity**, not raw endpointId, so a RejoinRequest
 *   never re-triggers the chime.
 * - Cleared on [onSessionEnded]; a new session triggers a fresh greet.
 *
 * ## Audio focus (Section 6)
 * - Requests [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK] only.
 * - On denial: silent no-op, never retries, never queues.
 * - Releases focus after the clip's measured duration ([CHIME_DURATION_MS]).
 */
class GuestGreetingManager(private val context: Context) {

    companion object {
        private const val TAG = "GuestGreetingMgr"

        // Section 5 — five interchangeable chime variants; order is irrelevant
        private val GREETING_RES_IDS = listOf(
            R.raw.guest_greet_chime_01,
            R.raw.guest_greet_chime_02,
            R.raw.guest_greet_chime_03,
            R.raw.guest_greet_chime_04,
            R.raw.guest_greet_chime_05,
        )

        // Section 6.4 — apply to Guest's current volume so the chime is never
        // jarringly louder than the session audio. 0.6 is the starting point;
        // tune during BE §16 calibration.
        private const val CHIME_VOLUME_SCALE = 0.6f

        // Section 5 — measured on real hardware: the longest clip is
        // guest_greet_chime_04.ogg at 3,631 ms (44.1 kHz). Rounded up so audio focus
        // is never abandoned mid-playback (BE §14.5 / §14.6.3).
        private const val CHIME_DURATION_MS = 4_000L
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var loadedSoundIds: List<Int> = emptyList()
    private val greetedThisSession = mutableSetOf<String>()

    // ── Settings toggle (BE §14.7) ───────────────────────────────────

    /** Live read of the DataStore-persisted greeting flag (FE §9.7). Default ON. */
    private fun isGreetingEnabled(): Boolean {
        val app = context.applicationContext as? HearYetApplication ?: return true
        return app.applicationPreferences.value.greetingChimeEnabled
    }

    // ── Lifecycle (Section 2.1 / 2.3) ─────────────────────────────────

    /**
     * Preload all five chime clips into [SoundPool].
     *
     * Call when the Guest enters [com.hearyet.app.core.model.SessionState.Discovering] —
     * matching BE §1's contextual-permission discipline: nothing is allocated
     * before the feature that needs it is actually invoked.
     */
    fun preload() {
        if (loadedSoundIds.isNotEmpty()) return // already preloaded
        Log.d(TAG, "Preloading ${GREETING_RES_IDS.size} chime clips")
        loadedSoundIds = GREETING_RES_IDS.map { soundPool.load(context, it, 1) }
    }

    /**
     * Play one randomly chosen chime if this guest identity hasn't been
     * greeted yet this session AND the Settings toggle is enabled.
     *
     * @param guestIdentity The rejoin identity (BE §10.2's persisted identity),
     *   NOT the raw transient endpointId.  Using the raw endpointId would cause
     *   a rejoin to look like a fresh join and re-trigger the chime (Section 4.2).
     * @param currentGuestVolume Live read from [GuestVolumeSlider]'s current
     *   value at the exact moment of playback (Section 6.4).
     */
    fun maybeGreet(guestIdentity: String, currentGuestVolume: Float) {
        // BE §14.7 — check the Settings toggle first; if OFF it's a no-op.
        if (!isGreetingEnabled()) return

        if (guestIdentity in greetedThisSession) return
        greetedThisSession += guestIdentity
        playChime(currentGuestVolume)
    }

    /**
     * BE §14.4.2/§17.13 — seed the greeted set with the identity persisted when the
     * chime first played this session, so a restore rejoin (process death) is never
     * re-greeted. Called by [com.hearyet.app.sync.SessionCoordinator] on the restore
     * path only; the set is still cleared on [onSessionEnded] so a new session
     * greets again (§14.4.3).
     */
    fun seedGreetedIdentity(identity: String?) {
        if (identity != null) {
            Log.d(TAG, "Seeding greeted identity from persisted session state")
            greetedThisSession += identity
        }
    }

    /**
     * Clear the per-session greeted-identity set.
     *
     * Call at the same point [SessionCoordinator] clears session state on
     * [com.hearyet.app.core.model.SessionState.Ended] or explicit Leave
     * (BE §10.2).  A new session will trigger a fresh greet.
     */
    fun onSessionEnded() {
        Log.d(TAG, "Session ended — clearing greeted set (size=${greetedThisSession.size})")
        greetedThisSession.clear()
    }

    /**
     * Release the [SoundPool] instance.
     *
     * Call wherever the Guest's audio foreground service is torn down
     * (BE §10's foreground-service teardown point).
     */
    fun release() {
        Log.d(TAG, "Releasing SoundPool")
        soundPool.release()
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun playChime(currentGuestVolume: Float) {
        if (loadedSoundIds.isEmpty()) {
            Log.w(TAG, "playChime: no sounds loaded — preload() not called or not finished")
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Section 6.1 — AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, never GAIN
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* no-op: transient, released explicitly below */ }
            .build()

        // Section 6.5 — on denial: silent no-op, never retry, never queue
        val granted = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) {
            Log.d(TAG, "playChime: audio focus denied — skipping")
            return
        }

        // Section 6.4 — scale by CHIME_VOLUME_SCALE, clamped to [0, 1]
        val volume = (currentGuestVolume * CHIME_VOLUME_SCALE).coerceIn(0f, 1f)

        // Pick a random chime variant — all five are interchangeable (Section 5)
        val soundId = loadedSoundIds.random()
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
        Log.d(TAG, "playChime: playing soundId=$soundId volume=$volume")

        // Section 6.3 — release focus after measured clip duration
        Handler(Looper.getMainLooper()).postDelayed(
            { audioManager.abandonAudioFocusRequest(focusRequest) },
            CHIME_DURATION_MS
        )
    }
}

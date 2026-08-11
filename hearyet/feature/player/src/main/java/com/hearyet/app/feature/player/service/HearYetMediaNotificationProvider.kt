package com.hearyet.app.feature.player.service

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.hearyet.app.core.model.SessionHolder
import com.hearyet.app.core.model.SessionState

/**
 * Custom MediaNotificationProvider for HearYet — FE §9.13 Host variant.
 *
 * Media3 1.10's `DefaultMediaNotificationProvider.createNotification` is final,
 * so this wraps it by composition and decorates the returned notification with
 * the session-specific content the spec requires while keeping the standard
 * transport actions:
 * - a subtitle line with the live guest count ("3 guests connected"), and
 * - the "HearYet session" fallback title when the media title is unavailable.
 *
 * The decoration applies only while a Host session is active and degrades
 * gracefully (returns the base notification unchanged) on any failure or on
 * API < 24 where notification recovery is unavailable.
 */
@UnstableApi
class HearYetMediaNotificationProvider(context: Context) : MediaNotification.Provider {

    private val appContext = context.applicationContext
    private val delegate = DefaultMediaNotificationProvider(context)

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val base = delegate.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback,
        )
        return decorateWithSessionInfo(base)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()

    private fun decorateWithSessionInfo(base: MediaNotification): MediaNotification {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return base
        val session = SessionHolder.active ?: return base
        val state = session.sessionState.value
        if (!session.isHost || state is SessionState.Idle || state is SessionState.Ended) {
            return base
        }
        return try {
            // androidx.core 1.19 removed NotificationCompat.Builder.recoverBuilder, so the
            // subtitle/title are injected into the produced notification's extras directly —
            // the same storage NotificationCompat.Builder.setSubText/setContentTitle use.
            val extras = base.notification.extras ?: return base
            val guestCount = session.hostGuestCount.value
            extras.putCharSequence(
                Notification.EXTRA_SUB_TEXT,
                when {
                    guestCount == 0 -> "No guests connected"
                    guestCount == 1 -> "1 guest connected"
                    else -> "$guestCount guests connected"
                },
            )
            // FE §9.13 — fallback title when the current media has no title metadata.
            if (extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank()) {
                extras.putCharSequence(Notification.EXTRA_TITLE, "HearYet session")
            }
            base
        } catch (_: Exception) {
            base
        }
    }
}

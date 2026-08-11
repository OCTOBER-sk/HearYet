package com.hearyet.app.feature.session.guest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hearyet.app.MainActivity
import com.hearyet.app.R
import com.hearyet.app.core.model.SessionHolder

/**
 * FE §9.13 — Guest foreground service.
 *
 * BE §10 requires the session to run inside a foreground service so guest audio
 * survives backgrounding and Doze. This service owns the persistent,
 * non-dismissible "Listening in sync" notification while a guest session is
 * active, and exposes a lock-screen "Leave session" action.
 */
class GuestSessionService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val handle = SessionHolder.active
        val hostName = handle?.hostDisplayName
        val sessionCode = handle?.sessionCode
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // FE §9.13 — lock-screen leave routes through GuestLeaveConfirmActivity so the
        // same confirmation a deliberate in-app leave requires is never skipped.
        val leaveIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, GuestLeaveConfirmActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val subtitle = hostName
            ?: sessionCode?.let { "Session $it" }
            ?: "HearYet session"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash)
            .setContentTitle("Listening in sync")
            .setContentText(subtitle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(contentIntent)
            .addAction(0, "Leave session", leaveIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HearYet session",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "hearyet_session"
        private const val NOTIFICATION_ID = 1001
    }
}

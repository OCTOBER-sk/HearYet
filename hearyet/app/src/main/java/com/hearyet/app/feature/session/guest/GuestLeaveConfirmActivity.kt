package com.hearyet.app.feature.session.guest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hearyet.app.core.model.SessionHolder
import com.hearyet.app.core.ui.component.ConfirmationDialog
import com.hearyet.app.core.ui.theme.HearYetTheme

/**
 * FE §9.13 — lock-screen "Leave session" confirmation.
 *
 * The guest foreground-service notification's "Leave session" action opens this
 * activity so a lock-screen tap routes through the *same* [ConfirmationDialog]
 * a deliberate in-app leave requires — never a bare single tap that silently
 * drops the guest.
 */
class GuestLeaveConfirmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HearYetTheme {
                ConfirmationDialog(
                    title = "Leave this session?",
                    message = "You'll need to scan or enter the host's code to rejoin.",
                    onDismiss = { finish() },
                    onConfirm = {
                        SessionHolder.active?.leaveSession()
                        stopService(Intent(this@GuestLeaveConfirmActivity, GuestSessionService::class.java))
                        finish()
                    },
                    confirmLabel = "Leave",
                    destructive = true,
                )
            }
        }
    }
}

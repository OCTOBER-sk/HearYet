package com.hearyet.app.core.ui.component

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hearyet.app.core.ui.color.HearYetColors

/**
 * M3 snackbar host restyled for HearYet — FE Addendum §17.
 *
 * `SurfaceRaised` background, `Accent` action text. Used exclusively for
 * non-fatal confirmations ("Code copied", "Name updated"). Never touches
 * Section 9.11's SessionError territory.
 */
@Composable
fun HearYetSnackbarHost(
    hostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SnackbarHost(
        hostState = hostState,
        snackbar = { data: SnackbarData ->
            Snackbar(
                snackbarData = data,
                containerColor = HearYetColors.SurfaceRaised,
                contentColor = HearYetColors.OnBackground,
                actionColor = HearYetColors.Accent,
            )
        },
    )
}

/**
 * Shows a short-duration snackbar message.
 */
suspend fun SnackbarHostState.showMessage(
    message: String,
    actionLabel: String? = null,
) {
    showSnackbar(
        message = message,
        actionLabel = actionLabel,
    )
}

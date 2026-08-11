package com.hearyet.app.feature.session.ended

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.ui.theme.HearYetTheme

/**
 * Session Ended screen — FE §9.8.
 *
 * Guest-only, triggered by [com.hearyet.app.transport.ControlMessage.SessionEnded].
 * Calm icon (not alarming — the host ending a session is normal), headline,
 * one supporting sentence, "Back to Home" CTA.
 */
@Composable
fun SessionEndedScreen(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Calm icon — not an alarming error icon (FE §9.8)
            Icon(
                imageVector = NextIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = HearYetColors.OnSurfaceMuted,
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "The host ended this session.",
                style = MaterialTheme.typography.headlineMedium,
                color = HearYetColors.OnBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "Create or join a new session when you're ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Button(
                onClick = onBackToHome,
                modifier = Modifier.widthIn(min = 200.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HearYetColors.Accent,
                    contentColor = HearYetColors.OnPrimary,
                ),
            ) {
                Text(
                    text = "Back to Home",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SessionEndedScreenPreview() {
    HearYetTheme {
        SessionEndedScreen(onBackToHome = {})
    }
}

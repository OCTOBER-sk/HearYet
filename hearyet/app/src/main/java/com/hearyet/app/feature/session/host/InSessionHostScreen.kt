package com.hearyet.app.feature.session.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.model.GuestInfo
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.ConfirmationDialog
import com.hearyet.app.core.ui.component.GuestAvatarStack
import com.hearyet.app.core.ui.component.HostSessionPanel
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.SyncHealthDot
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.qr.QrGenerator

/**
 * In-session Host screen — FE §9.6 Host variant.
 *
 * Shown when the Host returns to the app while a session is active.
 * Contains a session-info pill that opens the [HostSessionPanel] side sheet.
 * The actual player surface lives in PlayerActivity (Watch flow).
 */
@Composable
fun InSessionHostScreen(
    sessionCode: String?,
    guests: List<GuestInfo>,
    sessionStartedAtMs: Long,
    onOpenPlayer: () -> Unit,
    onEndSession: () -> Unit,
    onLeaveScreen: () -> Unit,
    modifier: Modifier = Modifier,
    qrPayload: String? = null,
) {
    var showSessionPanel by remember { mutableStateOf(false) }
    var showEndConfirmation by remember { mutableStateOf(false) }

    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { QrGenerator.generate(it).asImageBitmap() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo placeholder
                Icon(
                    imageVector = NextIcons.Session,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = HearYetColors.Accent,
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = "You're hosting a session.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = HearYetColors.OnBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = if (guests.isEmpty()) "Waiting for guests to join."
                    else if (guests.size == 1) "1 guest connected."
                    else "${guests.size} guests connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HearYetColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Open player button
                Button(
                    onClick = onOpenPlayer,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HearYetColors.Accent,
                        contentColor = HearYetColors.OnPrimary,
                    ),
                ) {
                    Text(
                        text = "Return to player",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // View guest list pill — guest-count/sync-health badge (§9.6, §18)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showSessionPanel = true },
                    shape = MaterialTheme.shapes.medium,
                    color = HearYetColors.SurfaceRaised,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // §18 — GuestAvatarStack alongside the text (never a replacement)
                        GuestAvatarStack(displayNames = guests.map { it.displayName })
                        if (guests.isNotEmpty()) {
                            Spacer(modifier = Modifier.size(Spacing.sm))
                        }
                        // Aggregate sync health — worst connected guest (§9.6)
                        val aggregateHealth = guests.minByOrNull { it.syncHealth.ordinal }?.syncHealth
                            ?: SyncHealth.GOOD
                        SyncHealthDot(health = aggregateHealth)
                        Text(
                            text = if (guests.isEmpty()) "No guests"
                            else if (guests.size == 1) "1 guest"
                            else "${guests.size} guests",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HearYetColors.OnBackground,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // End session action
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl),
                onClick = { showEndConfirmation = true },
                shape = MaterialTheme.shapes.medium,
                color = HearYetColors.Surface,
            ) {
                Text(
                    text = "End session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HearYetColors.SyncPoor,
                    modifier = Modifier.padding(Spacing.md),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── Host session side sheet ─────────────────────────────────
        HostSessionPanel(
            visible = showSessionPanel,
            sessionCode = sessionCode,
            guests = guests,
            qrBitmap = qrBitmap,
            sessionStartedAtMs = sessionStartedAtMs,
            onDismiss = { showSessionPanel = false },
            onEndSession = {
                showSessionPanel = false
                onEndSession()
            },
        )
    }

    // ── End session confirmation ─────────────────────────────────
    if (showEndConfirmation) {
        ConfirmationDialog(
            title = "End session for everyone?",
            message = "Every connected guest will be disconnected immediately.",
            onDismiss = { showEndConfirmation = false },
            onConfirm = {
                showEndConfirmation = false
                onEndSession()
            },
            confirmLabel = "End session",
            destructive = true,
        )
    }
}

@Preview
@Composable
private fun InSessionHostScreenNoGuestsPreview() {
    HearYetTheme {
        InSessionHostScreen(
            sessionCode = "A1B2C3",
            guests = emptyList(),
            sessionStartedAtMs = System.currentTimeMillis() - 300_000,
            onOpenPlayer = {},
            onEndSession = {},
            onLeaveScreen = {},
        )
    }
}

@Preview
@Composable
private fun InSessionHostScreenWithGuestsPreview() {
    HearYetTheme {
        InSessionHostScreen(
            sessionCode = "A1B2C3",
            guests = listOf(
                GuestInfo("1", "Alice", 5.0, 20L, 3.0, SyncHealth.GOOD, System.currentTimeMillis()),
                GuestInfo("2", "Bob", 12.0, 35L, 8.0, SyncHealth.DEGRADED, System.currentTimeMillis()),
            ),
            sessionStartedAtMs = System.currentTimeMillis() - 1_200_000,
            onOpenPlayer = {},
            onEndSession = {},
            onLeaveScreen = {},
        )
    }
}

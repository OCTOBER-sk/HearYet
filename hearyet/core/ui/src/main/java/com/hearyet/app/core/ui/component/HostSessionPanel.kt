package com.hearyet.app.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.model.GuestInfo
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.ConfirmationDialog
import com.hearyet.app.core.ui.component.QrFrame
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.SyncHealthDot


/**
 * Host in-session side sheet — FE §9.6 Host variant.
 *
 * Slides from the right edge over the dimmed player. Contains:
 * - Collapsed QR/code header (session already exists)
 * - Full guest list with per-guest [SyncHealthDot] + name + drift
 * - "End session" action behind [ConfirmationDialog]
 */
@Composable
fun HostSessionPanel(
    visible: Boolean,
    sessionCode: String?,
    guests: List<GuestInfo>,
    onDismiss: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
    qrBitmap: ImageBitmap? = null,
    sessionStartedAtMs: Long = 0L, // §18 — session duration tracking
    // H-4 — true while the Host's media is paused, so the status line reflects
    // "Paused" instead of always claiming "Live"/"Playing".
    isPaused: Boolean = false,
    // §4.5 — sheet slides branch on reduced-motion; snap() instead of animating.
    reduceMotion: Boolean = rememberMotionPreferences().reduceMotion,
) {
    var showEndConfirmation by remember { mutableStateOf(false) }

    // Dim scrim — tap to dismiss
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(snap()) else fadeIn(),
        exit = if (reduceMotion) fadeOut(snap()) else fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HearYetColors.Background.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        )
    }

    // Side sheet — slides from right edge
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) {
            fadeIn(snap())
        } else {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        },
        exit = if (reduceMotion) {
            fadeOut(snap())
        } else {
            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .background(HearYetColors.SurfaceRaised)
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xl, bottom = Spacing.lg),
        ) {
            // ── Header: Session code + QR (collapsed) ────────────────
            Text(
                text = "Session",
                style = MaterialTheme.typography.headlineMedium,
                color = HearYetColors.OnBackground,
            )

            // §18 — Session duration + live status
            if (sessionStartedAtMs > 0L) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                val elapsedMin = (System.currentTimeMillis() - sessionStartedAtMs) / 60_000L
                val guestCount = guests.size
                val statusLabel = if (isPaused) "Paused" else "Live"
                Text(
                    text = if (guestCount == 0) "${elapsedMin} min · $statusLabel"
                    else "${elapsedMin} min · $guestCount guest${if (guestCount != 1) "s" else ""} · $statusLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = HearYetColors.Accent,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            sessionCode?.let { code ->
                val clipboardManager = LocalClipboardManager.current

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrFrame(bitmap = qrBitmap)

                    Spacer(modifier = Modifier.height(Spacing.md))

                    SelectionContainer {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = HearYetColors.OnBackground,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(HearYetColors.Surface)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // ── Guest list ───────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = if (guests.isEmpty()) "No guests connected"
                    else if (guests.size == 1) "1 guest"
                    else "${guests.size} guests",
                    style = MaterialTheme.typography.labelLarge,
                    color = HearYetColors.OnSurfaceMuted,
                )
                // §18 — GuestAvatarStack additive to text
                if (guests.isNotEmpty()) {
                    GuestAvatarStack(
                        displayNames = guests.map { it.displayName },
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(guests, key = { it.endpointId }) { guest ->
                    GuestRow(guest = guest)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // ── End session — destructive, requires confirmation ─────
            Button(
                onClick = { showEndConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HearYetColors.Surface,
                    contentColor = HearYetColors.SyncPoor,
                ),
            ) {
                Text(
                    text = "End session",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    // ── End session confirmation dialog ──────────────────────────
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

@Composable
private fun GuestRow(guest: GuestInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(HearYetColors.Surface)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyncHealthDot(health = guest.syncHealth)

        Text(
            text = guest.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = HearYetColors.OnBackground,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "%.0f ms".format(guest.driftMs),
            style = MaterialTheme.typography.labelSmall,
            color = when (guest.syncHealth) {
                SyncHealth.GOOD -> HearYetColors.SyncGood
                SyncHealth.DEGRADED -> HearYetColors.SyncDegraded
                SyncHealth.POOR -> HearYetColors.SyncPoor
            },
        )
    }
}

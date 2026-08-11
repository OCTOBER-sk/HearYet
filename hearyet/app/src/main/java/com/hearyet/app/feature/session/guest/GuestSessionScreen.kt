package com.hearyet.app.feature.session.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.model.SessionError
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.ConfirmationDialog
import com.hearyet.app.core.ui.component.GuestVolumeSlider
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.SyncHealthDot
import com.hearyet.app.core.ui.component.sessionErrorMessage
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.ui.theme.HearYetTheme

@Composable
fun GuestSessionScreen(
    syncHealth: SyncHealth?,
    onLeaveSession: () -> Unit,
    hostUnreachable: Boolean = false,
    // M-5 — the actual terminal error (if any) plus its underlying detail, so an
    // Error state renders the real reason instead of a lying "Listening in sync".
    error: SessionError? = null,
    errorDetail: String? = null,
    modifier: Modifier = Modifier,
    hostDisplayName: String? = null,
    sessionCode: String? = null,
    guestCount: Int = 0,
    // BE §6:356 — guest-local volume: the value + change callback are wired by the
    // nav graph to SessionCoordinator's guestVolumeState, which controls the session
    // AudioTrack's own gain only (never the system STREAM_MUSIC volume).
    volume: Float = 1.0f,
    onVolumeChange: (Float) -> Unit = {},
) {
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(HearYetColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = this.maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = NextIcons.Home,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = HearYetColors.OnSurfaceVariant.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                Text(
                    // M-5 — "Listening in sync" only when genuinely playing: an
                    // Error state renders its actual reason (HOST_UNREACHABLE keeps
                    // the established "Connection lost" headline).
                    text = when {
                        hostUnreachable -> "Connection lost"
                        error != null -> sessionErrorMessage(error)
                        else -> "Listening in sync"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = HearYetColors.OnBackground,
                    textAlign = TextAlign.Center,
                )

                if (!hostDisplayName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "Hosted by $hostDisplayName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HearYetColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // M-5 — never claim sync while in an Error state: show the
                    // actual error text (with its detail when present) and a POOR
                    // dot instead of "In sync".
                    SyncHealthDot(
                        health = if (error != null) SyncHealth.POOR else (syncHealth ?: SyncHealth.GOOD),
                    )
                    Text(
                        text = when {
                            error != null -> errorDetail?.takeIf { it.isNotBlank() } ?: sessionErrorMessage(error)
                            syncHealth == SyncHealth.GOOD -> "In sync"
                            syncHealth == SyncHealth.DEGRADED -> "Sync drifting"
                            else -> "Connection poor"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HearYetColors.OnSurfaceMuted,
                    )
                }

                if (sessionCode != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Surface(
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
                            Text(
                                text = "Session",
                                style = MaterialTheme.typography.labelLarge,
                                color = HearYetColors.OnSurfaceMuted,
                            )
                            Text(
                                text = sessionCode,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = HearYetColors.OnBackground,
                            )
                            if (guestCount > 0) {
                                Text(
                                    text = "· $guestCount guest${if (guestCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = HearYetColors.OnSurfaceMuted,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                GuestVolumeSlider(
                    value = volume,
                    onValueChange = onVolumeChange,
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hostUnreachable) {
                    Button(
                        onClick = onLeaveSession,
                        modifier = Modifier.fillMaxWidth(),
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
                } else {
                    OutlinedButton(
                        onClick = { showLeaveConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HearYetColors.Error,
                        ),
                    ) {
                        Text(
                            text = "Leave session",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    if (showLeaveConfirmation) {
        ConfirmationDialog(
            title = "Leave this session?",
            message = "You'll need to scan or enter the host's code to rejoin.",
            onDismiss = { showLeaveConfirmation = false },
            onConfirm = {
                showLeaveConfirmation = false
                onLeaveSession()
            },
            confirmLabel = "Leave",
            destructive = true,
        )
    }
}

@Preview
@Composable
private fun GuestSessionScreenGoodPreview() {
    HearYetTheme {
        GuestSessionScreen(
            syncHealth = SyncHealth.GOOD,
            onLeaveSession = {},
        )
    }
}

@Preview
@Composable
private fun GuestSessionScreenPoorPreview() {
    HearYetTheme {
        GuestSessionScreen(
            syncHealth = SyncHealth.POOR,
            onLeaveSession = {},
        )
    }
}

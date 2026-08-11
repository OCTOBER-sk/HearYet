package com.hearyet.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.model.GuideCardType
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.ActionCard
import com.hearyet.app.core.ui.component.GuideCard
import com.hearyet.app.core.ui.component.Spacing
import com.hearyet.app.core.ui.component.SyncHealthDot
import com.hearyet.app.core.ui.component.rememberMotionPreferences
import com.hearyet.app.core.ui.component.safeContentPadding
import com.hearyet.app.core.ui.designsystem.NextIcons
import kotlinx.coroutines.delay

/**
 * Home — Watch / Create / Join action cards in the HearYet card style,
 * a calm welcome block, and the four dismissible guide cards below.
 */
@Composable
fun HomeScreen(
    onWatchClick: () -> Unit,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onReturnToSessionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isHostInActiveSession: Boolean = false,
    isGuestInActiveSession: Boolean = false,
    activeSessionSyncHealth: SyncHealth? = null,
    guideCardsDismissed: Set<GuideCardType> = emptySet(),
    onDismissGuideCard: (GuideCardType) -> Unit = {},
) {
    val hasActiveSession = isHostInActiveSession || isGuestInActiveSession

    val reduceMotion = rememberMotionPreferences().reduceMotion
    var cardsVisible by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(100)
            cardsVisible = true
        }
    }

    // §20 — staggered entrance: welcome, then each card 40ms apart, then guides.
    val baseEnter = if (reduceMotion) fadeIn(snap())
    else fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
    val delayed40 = if (reduceMotion) fadeIn(snap())
    else fadeIn(tween(300, delayMillis = 40)) + slideInVertically(tween(300, delayMillis = 40)) { it / 4 }
    val delayed80 = if (reduceMotion) fadeIn(snap())
    else fadeIn(tween(300, delayMillis = 80)) + slideInVertically(tween(300, delayMillis = 80)) { it / 4 }
    val delayed120 = if (reduceMotion) fadeIn(snap())
    else fadeIn(tween(300, delayMillis = 120)) + slideInVertically(tween(300, delayMillis = 120)) { it / 4 }
    val delayed160 = if (reduceMotion) fadeIn(snap())
    else fadeIn(tween(300, delayMillis = 160)) + slideInVertically(tween(300, delayMillis = 160)) { it / 4 }

    Scaffold(
        modifier = modifier,
        containerColor = HearYetColors.Background,
        contentWindowInsets = WindowInsets(0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = Spacing.lg),
        ) {
            item {
                // Top bar: logo mark + HearYet title top-left, settings top-right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "HearYet",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HearYetColors.OnBackground,
                    )
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = NextIcons.Settings,
                            contentDescription = "Settings",
                            tint = HearYetColors.OnSurfaceMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Active-session banner — tap to return to the live session
            if (hasActiveSession) {
                item {
                    AnimatedVisibility(visible = true, enter = baseEnter) {
                        ActiveSessionBanner(
                            syncHealth = activeSessionSyncHealth,
                            onClick = onReturnToSessionClick,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                }
            }

            // Welcome block — house icon + copy, exactly the Home empty state
            item {
                AnimatedVisibility(visible = cardsVisible, enter = baseEnter) {
                    WelcomeBlock()
                }
            }

            // Watch — media picker → player, zero session code in call stack
            item {
                AnimatedVisibility(visible = cardsVisible, enter = delayed40) {
                    Column {
                        ActionCard(
                            title = "Watch",
                            icon = NextIcons.Player,
                            onClick = onWatchClick,
                        )
                        if (isHostInActiveSession) {
                            Text(
                                text = "Return to session to play media",
                                style = MaterialTheme.typography.labelSmall,
                                color = HearYetColors.OnSurfaceDisabled,
                                modifier = Modifier.padding(start = Spacing.md),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            // Create — becomes the pulsing "Session Active" card while hosting
            item {
                AnimatedVisibility(visible = cardsVisible, enter = delayed80) {
                    ActionCard(
                        title = if (isHostInActiveSession) "Session Active" else "Create Session",
                        icon = NextIcons.Sensors,
                        onClick = onCreateClick,
                        isActiveSession = isHostInActiveSession,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            // Join — opens the Join flow
            item {
                AnimatedVisibility(visible = cardsVisible, enter = delayed120) {
                    ActionCard(
                        title = "Join Session",
                        icon = NextIcons.GroupAdd,
                        onClick = onJoinClick,
                    )
                }
            }

            // Guide cards — dismissible, persisted
            item {
                AnimatedVisibility(visible = cardsVisible, enter = delayed160) {
                    Column(
                        modifier = Modifier.padding(top = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (GuideCardType.WATCH !in guideCardsDismissed) {
                            GuideCard(
                                headline = "Watch your files",
                                description = "Open videos from your device and keep playback clean and smooth.",
                                icon = NextIcons.Player,
                                onDismiss = { onDismissGuideCard(GuideCardType.WATCH) },
                            )
                        }
                        if (GuideCardType.HEAR !in guideCardsDismissed) {
                            GuideCard(
                                headline = "Hear every detail",
                                description = "Listen with smart sound tuning made for the content you play.",
                                icon = NextIcons.Headphones,
                                onDismiss = { onDismissGuideCard(GuideCardType.HEAR) },
                            )
                        }
                        if (GuideCardType.CREATE_SESSION !in guideCardsDismissed) {
                            GuideCard(
                                headline = "Start a nearby session",
                                description = "Share audio with people close to you in a live listening room.",
                                icon = NextIcons.Sensors,
                                onDismiss = { onDismissGuideCard(GuideCardType.CREATE_SESSION) },
                            )
                        }
                        if (GuideCardType.JOIN_SESSION !in guideCardsDismissed) {
                            GuideCard(
                                headline = "Join someone nearby",
                                description = "Connect to an active session and stay in sync with the host.",
                                icon = NextIcons.GroupAdd,
                                onDismiss = { onDismissGuideCard(GuideCardType.JOIN_SESSION) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * Calm welcome block — the Home empty state: house icon, headline, one line
 * of supporting copy.
 */
@Composable
private fun WelcomeBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
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
            text = "Welcome to HearYet",
            style = MaterialTheme.typography.titleMedium,
            color = HearYetColors.OnBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = "Play your media or share it with people nearby — no internet, no accounts, everything stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = HearYetColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp),
        )
    }
}

@Composable
private fun ActiveSessionBanner(
    syncHealth: SyncHealth?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = HearYetColors.SurfaceRaised,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            syncHealth?.let {
                SyncHealthDot(health = it)
            }
            Text(
                text = "Live session — tap to return",
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.Accent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

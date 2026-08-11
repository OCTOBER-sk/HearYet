package com.hearyet.app.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.core.ui.R
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.component.SyncHealthDot
import com.hearyet.app.core.ui.designsystem.NextIcons
import com.hearyet.app.core.ui.extensions.copy
import com.hearyet.app.feature.player.buttons.PlayerButton

@OptIn(UnstableApi::class)
@Composable
fun ControlsTopView(
    modifier: Modifier = Modifier,
    title: String,
    onAudioClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onPlaybackSpeedClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    onSessionClick: () -> Unit = {},
    onBackClick: () -> Unit,
    // FE §9.6 — persistent host-session pill in the controls row (guest count +
    // aggregate sync health). Replaces the bare session icon while a host session
    // is active; tapping it is the single entry point into the session panel.
    hostSessionActive: Boolean = false,
    hostGuestCount: Int = 0,
    hostSyncHealth: SyncHealth? = null,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    // Add top spacing only when the system bars don't already provide it (e.g. on TV / landscape).
    val extraTopPadding = if (systemBarsPadding.calculateTopPadding() == 0.dp) 16.dp else 0.dp
    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(horizontal = 8.dp)
            .padding(bottom = 16.dp)
            .padding(top = extraTopPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlayerButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = null,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // FE §9.6 — persistent pill/badge (guest count + aggregate sync health)
            // when hosting; otherwise the plain start-session icon. One entry point.
            if (hostSessionActive) {
                Surface(
                    onClick = onSessionClick,
                    shape = RoundedCornerShape(50),
                    color = HearYetColors.SurfaceRaised,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // §10 — sync health is never color-only: the dot always keeps
                        // its paired text label (SyncHealthDot renders both).
                        hostSyncHealth?.let { SyncHealthDot(health = it) }
                        Text(
                            text = if (hostGuestCount == 1) "1 guest" else "$hostGuestCount guests",
                            style = MaterialTheme.typography.labelLarge,
                            color = HearYetColors.OnBackground,
                        )
                    }
                }
            } else {
                // BE §2.1 — turn the currently playing media into a live Host session.
                PlayerButton(onClick = onSessionClick) {
                    Icon(
                        imageVector = NextIcons.Session,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
            PlayerButton(onClick = onPlaylistClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist),
                    contentDescription = null,
                )
            }
            PlayerButton(onClick = onPlaybackSpeedClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = null,
                )
            }
            PlayerButton(onClick = onAudioClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_track),
                    contentDescription = null,
                )
            }
            PlayerButton(onClick = onSubtitleClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_subtitle_track),
                    contentDescription = null,
                )
            }
        }
    }
}

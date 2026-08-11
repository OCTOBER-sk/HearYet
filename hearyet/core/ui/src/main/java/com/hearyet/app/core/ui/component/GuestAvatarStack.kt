package com.hearyet.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.theme.HearYetPillShape

/**
 * Overlapping-circle stack showing guest initials/placeholder — FE Addendum §17.
 *
 * Additive to the existing guest-count pill text in the in-session Host UI (Section 9.6).
 * Uses a neutral Accent-tinted glyph — no photo avatars (GuestInfo has no photo field).
 */
@Composable
fun GuestAvatarStack(
    displayNames: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
) {
    if (displayNames.isEmpty()) return

    val visible = displayNames.take(maxVisible)
    val remaining = (displayNames.size - maxVisible).coerceAtLeast(0)

    Box(modifier = modifier) {
        visible.forEachIndexed { index, name ->
            val initial = name.firstOrNull()?.uppercase() ?: "?"
            val offsetX = (index * 16).dp // overlapping offset

            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .size(28.dp)
                    // §17 — avatar stack container uses the full/pill shape token.
                    .clip(HearYetPillShape)
                    .background(HearYetColors.AccentContainer)
                    .border(1.5.dp, HearYetColors.Background, HearYetPillShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = HearYetColors.Accent,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (remaining > 0) {
            val offsetX = (visible.size * 16).dp
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .size(28.dp)
                    .clip(HearYetPillShape)
                    .background(HearYetColors.SurfaceOutline)
                    .border(1.5.dp, HearYetColors.Background, HearYetPillShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = HearYetColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

package com.hearyet.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.model.ActivityKind
import com.hearyet.app.core.model.RecentActivityEntry
import com.hearyet.app.core.ui.color.HearYetColors
import com.hearyet.app.core.ui.designsystem.NextIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One card inside the RecentActivityShelf — FE Addendum §17.
 */
@Composable
fun RecentActivityCard(
    entry: RecentActivityEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relativeTime = rememberRelativeTime(entry.timestampMs)

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = HearYetColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .width(140.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            // §17 — MEDIA_PLAYED: thumbnail-or-accent-placeholder + filename. No
            // thumbnail extraction is built in v1 (thumbnailUri stays null), so the
            // accent placeholder block carries the visual weight.
            if (entry.kind == ActivityKind.MEDIA_PLAYED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(HearYetColors.AccentContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = NextIcons.Player,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = HearYetColors.Accent,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                when (entry.kind) {
                    ActivityKind.MEDIA_PLAYED -> Icon(
                        imageVector = NextIcons.Player,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = HearYetColors.Accent,
                    )
                    ActivityKind.SESSION_HOSTED -> Icon(
                        imageVector = NextIcons.Session,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = HearYetColors.Accent,
                    )
                    ActivityKind.SESSION_JOINED -> Icon(
                        imageVector = NextIcons.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = HearYetColors.Accent,
                    )
                }
                // §17 — MEDIA_PLAYED shows only icon + filename; relative time
                // belongs exclusively to SESSION_HOSTED / SESSION_JOINED.
                if (entry.kind != ActivityKind.MEDIA_PLAYED) {
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = HearYetColors.OnSurfaceMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = HearYetColors.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            entry.guestCount?.let { count ->
                if (count > 0 && entry.kind != ActivityKind.MEDIA_PLAYED) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = if (count == 1) "1 guest" else "$count guests",
                        style = MaterialTheme.typography.labelSmall,
                        color = HearYetColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return when {
        diff < 60_000 -> "Now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else -> {
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
            fmt.format(Date(timestampMs))
        }
    }
}

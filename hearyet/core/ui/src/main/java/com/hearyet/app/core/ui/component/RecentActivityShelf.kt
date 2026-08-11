package com.hearyet.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hearyet.app.core.model.RecentActivityEntry

/**
 * Horizontal LazyRow of up to 5 recent activity items — FE Addendum §17.
 *
 * Renders only when the list is non-empty. Shows skeleton placeholder
 * for the single frame where data is still being read.
 */
@Composable
fun RecentActivityShelf(
    entries: List<RecentActivityEntry>,
    onEntryClick: (RecentActivityEntry) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    if (isLoading) {
        // Show skeleton placeholders during the single-frame read gap
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(horizontal = Spacing.lg),
        ) {
            items(3) {
                SkeletonPlaceholder(
                    modifier = Modifier
                        .width(140.dp)
                        .height(80.dp),
                )
            }
        }
        return
    }

    if (entries.isEmpty()) return

    val displayEntries = entries.take(5)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
    ) {
        items(displayEntries, key = { it.id }) { entry ->
            RecentActivityCard(
                entry = entry,
                onClick = { onEntryClick(entry) },
            )
        }
    }
}

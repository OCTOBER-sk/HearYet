package com.hearyet.app.core.data.repository

import com.hearyet.app.core.model.ActivityKind
import com.hearyet.app.core.model.RecentActivityEntry
import kotlinx.coroutines.flow.Flow

/**
 * Recent Activity shelf data — FE Addendum §16.
 *
 * A single bounded list, newest-first, capped at 10 entries, device-local only.
 * Written from exactly two existing call sites:
 * 1. Media-open (Watch/player flow at playback start) → [ActivityKind.MEDIA_PLAYED]
 * 2. SessionCoordinator lifecycle callbacks → [ActivityKind.SESSION_HOSTED] / [ActivityKind.SESSION_JOINED]
 */
interface RecentActivityRepository {

    fun observeRecentActivity(): Flow<List<RecentActivityEntry>>

    suspend fun record(entry: RecentActivityEntry)
}

package com.hearyet.app.core.model

/**
 * Recent Activity data for the Home shelf — FE Addendum §16.
 *
 * Written from two existing call sites:
 * 1. Media-open (Watch/player flow at playback start) → MEDIA_PLAYED
 * 2. SessionCoordinator lifecycle callbacks → SESSION_HOSTED / SESSION_JOINED
 *
 * Capped at 10 entries, newest-first, device-local only.
 */
enum class ActivityKind {
    MEDIA_PLAYED,
    SESSION_HOSTED,
    SESSION_JOINED,
}

data class RecentActivityEntry(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val timestampMs: Long,
    val guestCount: Int? = null,
    val thumbnailUri: String? = null,
    val mediaUri: String? = null,
)

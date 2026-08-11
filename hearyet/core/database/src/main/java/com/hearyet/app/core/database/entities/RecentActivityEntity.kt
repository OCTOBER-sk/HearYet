package com.hearyet.app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the Recent Activity shelf — FE Addendum §16.
 *
 * Capped at 10 entries, written from exactly two existing call sites:
 * 1. Media-open (Watch/player flow at playback start)
 * 2. Session-lifecycle callback (SessionCoordinator)
 */
@Entity(tableName = "recent_activity")
data class RecentActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "kind")
    val kind: String, // "MEDIA_PLAYED" | "SESSION_HOSTED" | "SESSION_JOINED"

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    @ColumnInfo(name = "guest_count")
    val guestCount: Int? = null,

    @ColumnInfo(name = "thumbnail_uri")
    val thumbnailUri: String? = null,

    @ColumnInfo(name = "media_uri")
    val mediaUri: String? = null,
)

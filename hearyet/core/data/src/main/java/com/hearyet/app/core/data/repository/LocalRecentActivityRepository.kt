package com.hearyet.app.core.data.repository

import com.hearyet.app.core.database.dao.RecentActivityDao
import com.hearyet.app.core.database.entities.RecentActivityEntity
import com.hearyet.app.core.model.ActivityKind
import com.hearyet.app.core.model.RecentActivityEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LocalRecentActivityRepository @Inject constructor(
    private val recentActivityDao: RecentActivityDao,
) : RecentActivityRepository {

    override fun observeRecentActivity(): Flow<List<RecentActivityEntry>> =
        recentActivityDao.observeAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun record(entry: RecentActivityEntry) {
        recentActivityDao.insert(entry.toEntity())
        recentActivityDao.trimToMaxEntries()
    }

    private fun RecentActivityEntity.toModel() = RecentActivityEntry(
        id = id,
        kind = runCatching { ActivityKind.valueOf(kind) }.getOrDefault(ActivityKind.MEDIA_PLAYED),
        title = title,
        timestampMs = timestampMs,
        guestCount = guestCount,
        thumbnailUri = thumbnailUri,
        mediaUri = mediaUri,
    )

    private fun RecentActivityEntry.toEntity() = RecentActivityEntity(
        id = id,
        kind = kind.name,
        title = title,
        timestampMs = timestampMs,
        guestCount = guestCount,
        thumbnailUri = thumbnailUri,
        mediaUri = mediaUri,
    )
}

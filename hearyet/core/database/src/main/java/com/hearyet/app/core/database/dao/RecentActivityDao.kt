package com.hearyet.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hearyet.app.core.database.entities.RecentActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentActivityDao {

    @Query("SELECT * FROM recent_activity ORDER BY timestamp_ms DESC LIMIT 10")
    fun observeAll(): Flow<List<RecentActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RecentActivityEntity)

    @Query("DELETE FROM recent_activity WHERE id NOT IN (SELECT id FROM recent_activity ORDER BY timestamp_ms DESC LIMIT 10)")
    suspend fun trimToMaxEntries()
}

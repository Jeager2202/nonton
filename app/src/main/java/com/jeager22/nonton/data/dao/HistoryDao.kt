package com.jeager22.nonton.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jeager22.nonton.data.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 200")
    suspend fun getRecent(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Trim oldest entries beyond 200. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY watchedAt DESC LIMIT 200)")
    suspend fun trim()
}

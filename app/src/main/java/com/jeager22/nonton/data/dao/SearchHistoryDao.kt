package com.jeager22.nonton.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jeager22.nonton.data.entity.SearchHistoryEntity

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY createdAt DESC LIMIT 20")
    suspend fun getAll(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :q")
    suspend fun delete(q: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY createdAt DESC LIMIT 20)")
    suspend fun trim()
}

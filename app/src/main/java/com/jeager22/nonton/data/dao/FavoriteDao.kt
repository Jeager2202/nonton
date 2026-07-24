package com.jeager22.nonton.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jeager22.nonton.data.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Query("DELETE FROM favorites WHERE videoId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

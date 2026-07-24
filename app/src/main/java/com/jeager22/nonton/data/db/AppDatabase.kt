package com.jeager22.nonton.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jeager22.nonton.data.dao.FavoriteDao
import com.jeager22.nonton.data.dao.HistoryDao
import com.jeager22.nonton.data.dao.SearchHistoryDao
import com.jeager22.nonton.data.entity.FavoriteEntity
import com.jeager22.nonton.data.entity.HistoryEntity
import com.jeager22.nonton.data.entity.SearchHistoryEntity

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nonton.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

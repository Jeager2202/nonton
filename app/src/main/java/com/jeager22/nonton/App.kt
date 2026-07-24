package com.jeager22.nonton

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.jeager22.nonton.data.db.AppDatabase

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val playback = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Nonton Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio playback"
                setShowBadge(false)
            }
            nm.createNotificationChannel(playback)

            val download = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Nonton Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video and audio downloads"
                setShowBadge(false)
            }
            nm.createNotificationChannel(download)
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK = "nonton_playback"
        const val CHANNEL_DOWNLOAD = "nonton_download"

        lateinit var instance: App
            private set
        lateinit var database: AppDatabase
            private set
    }
}

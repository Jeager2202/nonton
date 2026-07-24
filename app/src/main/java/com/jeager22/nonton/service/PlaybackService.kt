package com.jeager22.nonton.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jeager22.nonton.App
import com.jeager22.nonton.MainActivity
import com.jeager22.nonton.R

/**
 * Foreground service untuk background audio playback.
 * Memakai MediaSession + MediaSessionService sehingga notification
 * otomatis memiliki tombol play/pause/stop dan terhubung ke sistem media.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build()
        player = exo

        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(pi)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p != null && !p.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.jeager22.nonton.PLAY"
        const val ACTION_PAUSE = "com.jeager22.nonton.PAUSE"
        const val ACTION_STOP = "com.jeager22.nonton.STOP"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"

        /** Build notification sederhana — fallback jika MediaSession tidak terpakai. */
        fun buildSimpleNotification(
            ctx: Service,
            title: String,
            text: String
        ): android.app.Notification {
            val pi = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(ctx, App.CHANNEL_PLAYBACK)
                .setSmallIcon(R.drawable.ic_stat_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }
}

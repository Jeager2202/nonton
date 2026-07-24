package com.jeager22.nonton.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Membungkus DownloadManager sistem untuk mengunduh video/audio ke
 * /storage/emulated/0/Download/Nonton/. Progress muncul di notification bar.
 */
class DownloadHelper(private val context: Context) {

    private val dm: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Mulai download. `item` adalah JSON dari format Invidious. */
    fun start(item: JSONObject, title: String, isAudio: Boolean): Long {
        val url = item.optString("url")
        if (url.isBlank()) {
            Toast.makeText(context, "URL stream tidak tersedia", Toast.LENGTH_SHORT).show()
            return -1
        }

        val ext = if (isAudio) "m4a" else "mp4"
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val fileName = "Nonton/${safeTitle}.${ext}"

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Nonton — $safeTitle")
            setDescription(if (isAudio) "Audio only" else "Video stream")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val id = dm.enqueue(request)
        Toast.makeText(context, "Download dimulai: $safeTitle", Toast.LENGTH_SHORT).show()
        return id
    }

    companion object {
        /** BroadcastReceiver sederhana untuk toast "Download selesai". */
        fun register(context: Context): BroadcastReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id != -1L) {
                        Toast.makeText(c, "Download selesai", Toast.LENGTH_SHORT).show()
                    }
                }
            }.also {
                ContextCompat.registerReceiver(
                    context, it,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
    }
}

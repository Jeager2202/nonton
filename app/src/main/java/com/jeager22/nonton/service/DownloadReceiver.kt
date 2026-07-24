package com.jeager22.nonton.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver untuk ACTION_DOWNLOAD_COMPLETE — menampilkan toast.
 * Dideklarasikan di AndroidManifest; kelas ini hanya men-forward ke helper.
 */
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Biarkan DownloadHelper.register yang menangani jika dipakai secara dinamis.
        // Receiver statis ini sebagai cadangan agar tidak crash di Android 14+.
    }
}

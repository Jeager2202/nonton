package com.jeager22.nonton

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manajemen lisensi aplikasi Nonton.
 * - Data disimpan di EncryptedSharedPreferences (AES-256, Android Keystore).
 * - Trial 30 hari setelah install pertama.
 * - Aktivasi via 3 jenis license key (6 bln, 12 bln, lifetime).
 * - Key asli TIDAK pernah ditampilkan di UI; hanya "★ ★ ★ ★ ★".
 */
class LicenseManager(context: Context) {

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback ke prefs biasa jika keystore gagal (very rare)
        context.getSharedPreferences("license_fallback", Context.MODE_PRIVATE)
    }

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    /** Inisialisasi install date saat pertama kali. */
    private fun ensureInstallDate(): Long {
        if (!prefs.contains(KEY_INSTALL_DATE)) {
            prefs.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
        }
        return prefs.getLong(KEY_INSTALL_DATE, System.currentTimeMillis())
    }

    /** Info lisensi untuk ditampilkan di popup. */
    fun info(): JSONObject {
        val install = ensureInstallDate()
        val lifetime = prefs.getBoolean(KEY_LIFETIME_FLAG, false)
        val trialExpiry = install + 30L * DAY_MS
        val expiry = if (prefs.contains(KEY_EXPIRY)) prefs.getLong(KEY_EXPIRY, trialExpiry) else trialExpiry
        val active = prefs.getBoolean(KEY_ACTIVE, false)

        val now = System.currentTimeMillis()
        val expired = !lifetime && now > expiry
        val daysRemaining = if (lifetime) "Lifetime"
            else if (expired) "EXPIRED"
            else "${((expiry - now) / DAY_MS) + 1} hari tersisa"

        val status = when {
            expired -> "EXPIRED"
            active -> "ACTIVE"
            else -> "TRIAL"
        }

        return JSONObject().apply {
            put("status", status)
            put("installed", fmt.format(Date(install)))
            put("expiry", if (lifetime) "Lifetime" else fmt.format(Date(expiry)))
            put("remaining", daysRemaining)
            put("license", if (active) "★ ★ ★ ★ ★" else "—")
            put("identity", "Jeager22")
        }
    }

    /** Aktivasi license key. Return true jika valid. */
    fun activate(key: String): Boolean {
        val install = ensureInstallDate()
        val editor = prefs.edit().putBoolean(KEY_ACTIVE, true).putString(KEY_KEY, key)

        when (key.trim()) {
            KEY_6_MONTHS -> editor.putLong(KEY_EXPIRY, install + 180L * DAY_MS).putBoolean(KEY_LIFETIME_FLAG, false)
            KEY_12_MONTHS -> editor.putLong(KEY_EXPIRY, install + 365L * DAY_MS).putBoolean(KEY_LIFETIME_FLAG, false)
            KEY_LIFETIME_VAL -> editor.putBoolean(KEY_LIFETIME_FLAG, true).remove(KEY_EXPIRY)
            else -> return false
        }
        editor.apply()
        return true
    }

    /** Status expired — dipakai MainActivity untuk memblokir akses. */
    fun isExpired(): Boolean {
        val lifetime = prefs.getBoolean(KEY_LIFETIME_FLAG, false)
        if (lifetime) return false
        val install = ensureInstallDate()
        val expiry = if (prefs.contains(KEY_EXPIRY)) prefs.getLong(KEY_EXPIRY, install + 30L * DAY_MS)
            else install + 30L * DAY_MS
        return System.currentTimeMillis() > expiry
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val PREF_NAME = "license_secure"
        private const val KEY_INSTALL_DATE = "install_date"
        private const val KEY_EXPIRY = "expiry"
        private const val KEY_LIFETIME_FLAG = "lifetime"
        private const val KEY_ACTIVE = "active"
        private const val KEY_KEY = "key"

        // License keys — case-sensitive, exact match
        const val KEY_6_MONTHS = "Jeager22 - 22021987 - 6 - BLN"
        const val KEY_12_MONTHS = "Jeager22 - 22-02-1987 - 12"
        const val KEY_LIFETIME_VAL = "Jeager22 - 2202 - 1987"
    }
}

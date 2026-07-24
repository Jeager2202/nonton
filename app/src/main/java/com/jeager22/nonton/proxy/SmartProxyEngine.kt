package com.jeager22.nonton.proxy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.math.min

/**
 * SmartProxyEngine + ProxyPool: God Mode, auto best, failover, stealth headers,
 * blacklist, top-5 intelligent pool, and emergency direct bypass.
 */
class SmartProxyEngine(private val context: Context) {
    enum class StealthLevel { LOW, MEDIUM, PARANOID }

    val pool = ProxyPool(context)
    private val tester = ProxyTester()
    private val prefs = context.getSharedPreferences("nonton_proxy_engine", Context.MODE_PRIVATE)

    @Volatile private var active: ProxyNode? = loadActive()
    @Volatile private var directMode: Boolean = prefs.getBoolean("direct", true)
    @Volatile private var autoRotate: Boolean = prefs.getBoolean("autoRotate", false)
    @Volatile private var lastRotate: Long = prefs.getLong("lastRotate", 0L)
    @Volatile private var stealth: StealthLevel = runCatching {
        StealthLevel.valueOf(prefs.getString("stealth", StealthLevel.MEDIUM.name) ?: StealthLevel.MEDIUM.name)
    }.getOrDefault(StealthLevel.MEDIUM)

    fun currentProxy(): Proxy? {
        rotateIfNeeded()
        val n = active
        if (directMode || n == null || n.isBlacklisted) return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(n.host, n.port))
    }

    fun currentNode(): ProxyNode? = if (directMode) null else active

    fun stealthHeaders(): Map<String, String> {
        val ua = when (stealth) {
            StealthLevel.LOW -> "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"
            StealthLevel.MEDIUM -> listOf(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
            ).random()
            StealthLevel.PARANOID -> listOf(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 Chrome/127 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 14; Xiaomi 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; V2303A) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
            ).random()
        }
        val headers = linkedMapOf(
            "User-Agent" to ua,
            "Accept" to "application/json,text/plain,*/*",
            "Accept-Language" to if (stealth == StealthLevel.PARANOID) listOf("id-ID,id;q=0.9,en-US;q=0.6", "en-US,en;q=0.9,id;q=0.7").random() else "id-ID,id;q=0.9,en-US;q=0.6",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache"
        )
        if (stealth == StealthLevel.PARANOID) {
            headers["DNT"] = "1"
            headers["Sec-Fetch-Site"] = "cross-site"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["X-Nonton-Stealth"] = System.currentTimeMillis().toString(16)
        }
        return headers
    }

    fun setStealth(level: String): JSONObject {
        stealth = runCatching { StealthLevel.valueOf(level.uppercase()) }.getOrDefault(StealthLevel.MEDIUM)
        prefs.edit().putString("stealth", stealth.name).apply()
        return statusJson("Stealth level set to ${stealth.name}")
    }

    fun setAutoRotate(on: Boolean): JSONObject {
        autoRotate = on
        prefs.edit().putBoolean("autoRotate", on).apply()
        return statusJson("Auto rotation ${if (on) "enabled" else "disabled"}")
    }

    fun emergencyDirect(): JSONObject {
        active = null
        directMode = true
        prefs.edit().putBoolean("direct", true).remove("active").apply()
        return statusJson("Emergency Direct active. Semua proxy dibypass.")
    }

    fun clearBlacklist(): JSONObject {
        pool.clearBlacklist()
        return statusJson("Blacklist cleared")
    }

    fun importCustom(lines: String): JSONObject {
        val count = pool.importLines(lines)
        return statusJson("Imported $count proxy")
    }

    fun scanFree(onLog: (String) -> Unit = {}): JSONObject {
        onLog("Scanning public proxy sources...")
        val list = tester.fetchFreeProxies(50)
        val added = pool.merge(list)
        onLog("Found ${list.size} proxy candidates, added $added new entries")
        return statusJson("Scan complete: ${list.size} candidates")
    }

    fun autoBest(onLog: (String) -> Unit = {}): JSONObject {
        val candidates = pool.candidates(30).ifEmpty {
            onLog("Pool empty, scanning first...")
            val scanned = tester.fetchFreeProxies(45)
            pool.merge(scanned)
            scanned
        }
        var best: ProxyNode? = null
        var tested = 0
        for (node in candidates.take(25)) {
            tested++
            onLog("Testing ${node.address} ...")
            val res = tester.test(node, stealthHeaders())
            if (res.ok) {
                pool.recordSuccess(node, res.latencyMs)
                onLog("LIVE ${node.address} ${res.latencyMs}ms")
                if (best == null || node.score() > best!!.score()) best = node
            } else {
                pool.recordFailure(node)
                onLog("DEAD ${node.address} ${res.error ?: "timeout"}")
            }
            if (best != null && tested >= 8) break
        }
        return if (best != null) activate(best!!, "AUTO BEST selected ${best!!.address}")
        else statusJson("No live proxy found. Emergency Direct remains available.")
    }

    fun godMode(onLog: (String) -> Unit = {}): JSONObject {
        stealth = StealthLevel.PARANOID
        autoRotate = true
        prefs.edit().putString("stealth", stealth.name).putBoolean("autoRotate", true).apply()
        onLog("GOD MODE: PARANOID stealth + auto rotation enabled")
        scanFree(onLog)
        val result = autoBest(onLog)
        return statusJson("GOD MODE complete: ${result.optString("message")}")
    }

    fun reportSuccess(latencyMs: Long) {
        active?.let { pool.recordSuccess(it, latencyMs) }
    }

    fun reportFailure() {
        val failed = active ?: return
        pool.recordFailure(failed)
        if (!directMode) {
            val next = pool.topAlive(5).firstOrNull { it.address != failed.address }
            if (next != null) activate(next, "Auto failover to ${next.address}")
        }
    }

    fun statusJson(message: String = "OK"): JSONObject {
        val top = pool.topAlive(5)
        val arr = JSONArray()
        top.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("message", message)
            .put("direct", directMode)
            .put("active", active?.toJson() ?: JSONObject.NULL)
            .put("stealth", stealth.name)
            .put("autoRotate", autoRotate)
            .put("top", arr)
            .put("pool", pool.toJson())
    }

    private fun activate(node: ProxyNode, message: String): JSONObject {
        active = node
        directMode = false
        lastRotate = System.currentTimeMillis()
        prefs.edit()
            .putBoolean("direct", false)
            .putLong("lastRotate", lastRotate)
            .putString("active", node.toJson().toString())
            .apply()
        return statusJson(message)
    }

    private fun rotateIfNeeded() {
        if (!autoRotate || directMode) return
        val now = System.currentTimeMillis()
        if (now - lastRotate < 45L * 60L * 1000L) return
        val top = pool.topAlive(5)
        if (top.size <= 1) return
        val current = active?.address
        val next = top.firstOrNull { it.address != current } ?: return
        activate(next, "Auto rotated to ${next.address}")
    }

    private fun loadActive(): ProxyNode? = runCatching {
        val raw = prefs.getString("active", null) ?: return null
        ProxyNode.fromJson(JSONObject(raw))
    }.getOrNull()
}

package com.jeager22.nonton.proxy

import org.json.JSONObject

/** Proxy record with lightweight scoring. Kept intentionally dependency-free. */
data class ProxyNode(
    val host: String,
    val port: Int,
    val scheme: String = "http",
    var latencyMs: Long = 9_999,
    var success: Int = 0,
    var failure: Int = 0,
    var lastSeen: Long = 0,
    var blacklistedUntil: Long = 0
) {
    val address: String get() = "$scheme://$host:$port"
    val isBlacklisted: Boolean get() = blacklistedUntil > System.currentTimeMillis()

    fun score(): Double {
        val total = (success + failure).coerceAtLeast(1)
        val successRate = success.toDouble() / total.toDouble()
        val latencyScore = (1_500.0 - latencyMs.coerceAtMost(1_500)).coerceAtLeast(0.0) / 1_500.0
        val freshness = if (System.currentTimeMillis() - lastSeen < 60L * 60L * 1000L) 0.12 else 0.0
        val penalty = if (isBlacklisted) 5.0 else 0.0
        return successRate * 0.62 + latencyScore * 0.31 + freshness - penalty
    }

    fun toJson(): JSONObject = JSONObject()
        .put("host", host)
        .put("port", port)
        .put("scheme", scheme)
        .put("address", address)
        .put("latencyMs", latencyMs)
        .put("success", success)
        .put("failure", failure)
        .put("lastSeen", lastSeen)
        .put("blacklistedUntil", blacklistedUntil)
        .put("score", score())
        .put("blacklisted", isBlacklisted)

    companion object {
        fun parse(line: String): ProxyNode? {
            val cleaned = line.trim().removePrefix("http://").removePrefix("https://")
            val parts = cleaned.split(":")
            if (parts.size < 2) return null
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535) return null
            return ProxyNode(host = host, port = port)
        }

        fun fromJson(o: JSONObject): ProxyNode = ProxyNode(
            host = o.optString("host"),
            port = o.optInt("port"),
            scheme = o.optString("scheme", "http"),
            latencyMs = o.optLong("latencyMs", 9_999),
            success = o.optInt("success", 0),
            failure = o.optInt("failure", 0),
            lastSeen = o.optLong("lastSeen", 0),
            blacklistedUntil = o.optLong("blacklistedUntil", 0)
        )
    }
}

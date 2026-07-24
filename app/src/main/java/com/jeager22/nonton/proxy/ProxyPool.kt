package com.jeager22.nonton.proxy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** Intelligent top-5 proxy pool with success/failure scoring and blacklist. */
class ProxyPool(context: Context) {
    private val prefs = context.getSharedPreferences("nonton_proxy_pool", Context.MODE_PRIVATE)
    private val nodes = CopyOnWriteArrayList<ProxyNode>()

    init { load() }

    @Synchronized fun importLines(lines: String): Int {
        var added = 0
        lines.lineSequence().mapNotNull { ProxyNode.parse(it) }.forEach { node ->
            if (nodes.none { it.host == node.host && it.port == node.port }) {
                nodes += node
                added++
            }
        }
        trimToReasonableSize()
        save()
        return added
    }

    @Synchronized fun merge(list: List<ProxyNode>): Int {
        var added = 0
        list.forEach { node ->
            if (nodes.none { it.host == node.host && it.port == node.port }) {
                nodes += node
                added++
            }
        }
        trimToReasonableSize()
        save()
        return added
    }

    fun all(): List<ProxyNode> = nodes.toList()

    fun topAlive(limit: Int = 5): List<ProxyNode> = nodes
        .filter { !it.isBlacklisted && it.success > 0 }
        .sortedByDescending { it.score() }
        .take(limit)

    fun candidates(limit: Int = 30): List<ProxyNode> = nodes
        .filter { !it.isBlacklisted }
        .sortedWith(compareByDescending<ProxyNode> { it.success == 0 }.thenByDescending { it.score() })
        .take(limit)

    fun best(): ProxyNode? = topAlive(5).firstOrNull()

    @Synchronized fun recordSuccess(node: ProxyNode, latency: Long) {
        val n = findOrAdd(node)
        n.success += 1
        n.latencyMs = latency
        n.lastSeen = System.currentTimeMillis()
        n.blacklistedUntil = 0
        save()
    }

    @Synchronized fun recordFailure(node: ProxyNode) {
        val n = findOrAdd(node)
        n.failure += 1
        if (n.failure >= 2 && n.failure > n.success + 1) {
            n.blacklistedUntil = System.currentTimeMillis() + 30L * 60L * 1000L
        }
        save()
    }

    @Synchronized fun clearBlacklist() {
        nodes.forEach { it.blacklistedUntil = 0 }
        save()
    }

    fun toJson(): JSONObject {
        val top = JSONArray()
        topAlive(5).forEach { top.put(it.toJson()) }
        val allArr = JSONArray()
        nodes.sortedByDescending { it.score() }.take(30).forEach { allArr.put(it.toJson()) }
        return JSONObject()
            .put("top", top)
            .put("all", allArr)
            .put("count", nodes.size)
    }

    private fun findOrAdd(node: ProxyNode): ProxyNode {
        val existing = nodes.firstOrNull { it.host == node.host && it.port == node.port }
        if (existing != null) return existing
        nodes += node
        return node
    }

    private fun trimToReasonableSize() {
        val sorted = nodes.sortedByDescending { it.score() }.take(80)
        nodes.clear()
        nodes.addAll(sorted)
    }

    private fun load() {
        nodes.clear()
        val raw = prefs.getString("nodes", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) nodes += ProxyNode.fromJson(arr.getJSONObject(i))
        } catch (_: Exception) {}
    }

    @Synchronized fun save() {
        val arr = JSONArray()
        nodes.sortedByDescending { it.score() }.take(80).forEach { arr.put(it.toJson()) }
        prefs.edit().putString("nodes", arr.toString()).apply()
    }
}

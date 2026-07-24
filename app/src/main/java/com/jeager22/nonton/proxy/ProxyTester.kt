package com.jeager22.nonton.proxy

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Scanner + tester for public HTTP proxies. */
class ProxyTester {
    private val directClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val sources = listOf(
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/http/data.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://proxy-list.download/api/v1/get?type=http"
    )

    fun fetchFreeProxies(max: Int = 45): List<ProxyNode> {
        val found = linkedSetOf<String>()
        for (url in sources) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "NontonProxyScanner/2.1").build()
                directClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val body = res.body?.string().orEmpty()
                    body.lineSequence().forEach { line ->
                        val n = ProxyNode.parse(line)
                        if (n != null) found += "${n.host}:${n.port}"
                    }
                }
            } catch (_: Exception) {}
            if (found.size >= max) break
        }
        return found.take(max).mapNotNull { ProxyNode.parse(it) }
    }

    fun test(node: ProxyNode, headers: Map<String, String> = emptyMap()): TestResult {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(node.host, node.port))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .callTimeout(11, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val reqBuilder = Request.Builder()
            .url("https://api64.ipify.org?format=json")
            .header("User-Agent", headers["User-Agent"] ?: "Mozilla/5.0")
            .header("Accept", "application/json,text/plain,*/*")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val start = System.currentTimeMillis()
        return try {
            client.newCall(reqBuilder.build()).execute().use { res ->
                val latency = System.currentTimeMillis() - start
                if (res.isSuccessful) TestResult(true, latency, null)
                else TestResult(false, latency, "HTTP ${res.code}")
            }
        } catch (e: Exception) {
            TestResult(false, System.currentTimeMillis() - start, e.message)
        }
    }

    data class TestResult(val ok: Boolean, val latencyMs: Long, val error: String?)
}

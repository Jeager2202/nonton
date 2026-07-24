package com.jeager22.nonton

import com.jeager22.nonton.proxy.SmartProxyEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Resilient API layer: Invidious first, Piped fallback, SmartProxyEngine failover.
 * All HTTP runs natively from Kotlin to bypass WebView CORS.
 */
class InvidiousApi(private val proxyEngine: SmartProxyEngine? = null) {

    private val directClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val instances: List<String> = listOf(
        "https://inv.nadeko.net",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de",
        "https://invidious.jing.rocks",
        "https://invidious.privacyredirect.com",
        "https://invidious.fdn.fr",
        "https://iv.ggtyler.dev",
        "https://invidious.lunar.icu"
    )

    private val pipedInstances: List<String> = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api-piped.mha.fi",
        "https://pipedapi.syncpundit.io",
        "https://pipedapi.tokhmi.xyz"
    )

    @Volatile private var preferred: String? = null
    @Volatile private var preferredPiped: String? = null

    fun get(path: String): String {
        val ordered = preferred?.let { listOf(it) + instances.filter { b -> b != it } } ?: instances
        var lastError = "Unknown error"
        for (base in ordered) {
            val start = System.currentTimeMillis()
            try {
                val req = request(base + path)
                client().newCall(req).execute().use { r ->
                    if (r.isSuccessful) {
                        preferred = base
                        proxyEngine?.reportSuccess(System.currentTimeMillis() - start)
                        return r.body?.string() ?: throw Exception("Empty body from $base")
                    }
                    lastError = "HTTP ${r.code} from $base"
                }
            } catch (e: Exception) {
                lastError = e.message ?: "network error"
                proxyEngine?.reportFailure()
            }
        }

        // Fallback berlapis ke Piped API untuk mengurangi ketergantungan Invidious.
        return when {
            path.startsWith("/api/v1/search") -> pipedSearch(path)
            path.startsWith("/api/v1/trending") -> pipedTrending(path)
            path.startsWith("/api/v1/videos/") -> pipedVideo(path.substringAfterLast('/'))
            else -> throw Exception("All API instances unavailable: $lastError")
        }
    }

    fun trending(region: String = "ID", type: String? = null): String {
        val q = "/api/v1/trending?region=${URLEncoder.encode(region, "UTF-8")}" +
                (type?.let { "&type=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
        return get(q)
    }

    fun search(query: String, sort: String = "relevance"): String {
        val q = "/api/v1/search?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&type=video&sort=${URLEncoder.encode(sort, "UTF-8")}"
        return get(q)
    }

    fun video(id: String): String = get("/api/v1/videos/${URLEncoder.encode(id, "UTF-8")}")

    fun pipedVideo(id: String): String {
        val raw = pipedGet("/streams/${URLEncoder.encode(id, "UTF-8")}")
        return convertPipedVideo(JSONObject(raw), id).toString()
    }

    private fun pipedSearch(path: String): String {
        val q = queryParam(path, "q") ?: ""
        val raw = pipedGet("/search?q=${URLEncoder.encode(q, "UTF-8")}&filter=videos")
        return convertPipedList(raw).toString()
    }

    private fun pipedTrending(path: String): String {
        val region = queryParam(path, "region") ?: "ID"
        val raw = pipedGet("/trending?region=${URLEncoder.encode(region, "UTF-8")}")
        return convertPipedList(raw).toString()
    }

    private fun pipedGet(path: String): String {
        val ordered = preferredPiped?.let { listOf(it) + pipedInstances.filter { b -> b != it } } ?: pipedInstances
        var last = "Unknown Piped error"
        for (base in ordered) {
            val start = System.currentTimeMillis()
            try {
                client().newCall(request(base + path)).execute().use { res ->
                    if (res.isSuccessful) {
                        preferredPiped = base
                        proxyEngine?.reportSuccess(System.currentTimeMillis() - start)
                        return res.body?.string().orEmpty()
                    }
                    last = "HTTP ${res.code} from $base"
                }
            } catch (e: Exception) {
                last = e.message ?: "Piped error"
                proxyEngine?.reportFailure()
            }
        }
        throw Exception("Piped fallback unavailable: $last")
    }

    private fun client(): OkHttpClient {
        val proxy = proxyEngine?.currentProxy()
        if (proxy == null) return directClient
        return directClient.newBuilder().proxy(proxy).build()
    }

    private fun request(url: String): Request {
        val b = Request.Builder().url(url)
        val headers = proxyEngine?.stealthHeaders() ?: mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) Nonton/2.1",
            "Accept" to "application/json,text/plain,*/*",
            "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.6"
        )
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun queryParam(path: String, key: String): String? {
        val q = path.substringAfter('?', "")
        return q.split('&').firstOrNull { it.substringBefore('=') == key }?.substringAfter('=', "")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private fun convertPipedList(raw: String): JSONArray {
        val arr = when {
            raw.trim().startsWith("[") -> JSONArray(raw)
            else -> JSONObject(raw).optJSONArray("items") ?: JSONObject(raw).optJSONArray("results") ?: JSONArray()
        }
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("type", o.optString("contentType", "stream"))
            if (type.isNotBlank() && !type.contains("stream", true) && !type.contains("video", true)) continue
            val id = videoIdFrom(o.optString("url", o.optString("videoId")))
            if (id.isBlank()) continue
            out.put(JSONObject()
                .put("videoId", id)
                .put("title", o.optString("title"))
                .put("author", o.optString("uploaderName", o.optString("author")))
                .put("authorId", o.optString("uploaderUrl", o.optString("authorId")))
                .put("lengthSeconds", o.optLong("duration", o.optLong("durationSeconds", 0)))
                .put("viewCount", o.optLong("views", o.optLong("viewCount", 0)))
                .put("videoThumbnails", JSONArray().put(JSONObject()
                    .put("quality", "piped")
                    .put("url", o.optString("thumbnail", o.optString("thumbnailUrl")))
                    .put("width", 320)
                    .put("height", 180)))
            )
        }
        return out
    }

    private fun convertPipedVideo(o: JSONObject, id: String): JSONObject {
        val adaptive = JSONArray()
        val videoStreams = o.optJSONArray("videoStreams") ?: JSONArray()
        for (i in 0 until videoStreams.length()) {
            val v = videoStreams.optJSONObject(i) ?: continue
            adaptive.put(JSONObject()
                .put("type", v.optString("format", "video/mp4"))
                .put("url", v.optString("url"))
                .put("qualityLabel", v.optString("quality", v.optString("qualityLabel")))
            )
        }
        val audioStreams = o.optJSONArray("audioStreams") ?: JSONArray()
        for (i in 0 until audioStreams.length()) {
            val a = audioStreams.optJSONObject(i) ?: continue
            adaptive.put(JSONObject()
                .put("type", a.optString("format", "audio/mp4"))
                .put("url", a.optString("url"))
                .put("qualityLabel", a.optString("quality", "audio"))
            )
        }
        val related = JSONArray()
        val rel = o.optJSONArray("relatedStreams") ?: JSONArray()
        for (i in 0 until rel.length()) {
            val r = rel.optJSONObject(i) ?: continue
            val rid = videoIdFrom(r.optString("url", r.optString("videoId")))
            if (rid.isBlank()) continue
            related.put(JSONObject()
                .put("videoId", rid)
                .put("title", r.optString("title"))
                .put("author", r.optString("uploaderName", r.optString("author")))
                .put("lengthSeconds", r.optLong("duration", 0))
                .put("viewCount", r.optLong("views", 0))
                .put("videoThumbnails", JSONArray().put(JSONObject().put("url", r.optString("thumbnail"))))
            )
        }
        return JSONObject()
            .put("videoId", id)
            .put("title", o.optString("title"))
            .put("author", o.optString("uploader", o.optString("uploaderName")))
            .put("authorId", o.optString("uploaderUrl"))
            .put("lengthSeconds", o.optLong("duration", 0))
            .put("viewCount", o.optLong("views", 0))
            .put("videoThumbnails", JSONArray().put(JSONObject().put("url", o.optString("thumbnailUrl", o.optString("thumbnail")))))
            .put("adaptiveFormats", adaptive)
            .put("formatStreams", JSONArray())
            .put("recommendedVideos", related)
            .put("source", "Piped fallback")
    }

    private fun videoIdFrom(urlOrId: String): String {
        if (urlOrId.length == 11 && !urlOrId.contains('/')) return urlOrId
        return urlOrId.substringAfter("watch?v=", urlOrId)
            .substringAfter("/watch/", urlOrId)
            .substringAfter("/v/", urlOrId)
            .substringBefore('&')
            .substringBefore('?')
            .substringBefore('/')
    }
}

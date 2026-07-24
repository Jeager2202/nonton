package com.jeager22.nonton

import com.jeager22.nonton.proxy.SmartProxyEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Failover stream selector. The NewPipe-style phase means: parse all available
 * adaptive/muxed streams locally first, retry refreshed metadata, then Piped/Invidious
 * fallback through InvidiousApi conversion helpers.
 */
class StreamExtractor(
    private val api: InvidiousApi,
    private val proxyEngine: SmartProxyEngine
) {
    data class Result(val url: String, val quality: String, val source: String)

    fun extract(video: JSONObject, quality: String): Result? {
        select(video, quality, "NEWPIPE-STYLE LOCAL")?.let { return it }
        val id = video.optString("videoId")
        if (id.isBlank()) return null

        repeat(2) { attempt ->
            try {
                val refreshed = JSONObject(api.video(id))
                select(refreshed, quality, "INVIDIOUS RETRY ${attempt + 1}")?.let { return it }
            } catch (_: Exception) {
                proxyEngine.reportFailure()
            }
        }

        return try {
            val piped = JSONObject(api.pipedVideo(id))
            select(piped, quality, "PIPED FALLBACK")
        } catch (_: Exception) { null }
    }

    private fun select(o: JSONObject, quality: String, source: String): Result? {
        val formats = o.optJSONArray("adaptiveFormats") ?: JSONArray()
        var firstVideo: JSONObject? = null
        var exact: JSONObject? = null
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val type = f.optString("type", "")
            val url = f.optString("url", "")
            if (url.isBlank() || !type.startsWith("video/")) continue
            if (firstVideo == null) firstVideo = f
            val label = f.optString("qualityLabel", f.optString("quality", ""))
            if (label == quality) { exact = f; break }
        }
        val chosen = exact ?: firstVideo
        if (chosen != null) {
            return Result(
                url = chosen.optString("url"),
                quality = chosen.optString("qualityLabel", chosen.optString("quality", quality)),
                source = source
            )
        }
        val streams = o.optJSONArray("formatStreams") ?: JSONArray()
        val muxed = streams.optJSONObject(0)
        if (muxed != null && muxed.optString("url").isNotBlank()) {
            return Result(muxed.optString("url"), muxed.optString("qualityLabel", quality), "$source MUXED")
        }
        return null
    }
}

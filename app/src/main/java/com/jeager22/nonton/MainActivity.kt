package com.jeager22.nonton

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.room.Room
import com.jeager22.nonton.data.db.AppDatabase
import com.jeager22.nonton.data.entity.FavoriteEntity
import com.jeager22.nonton.data.entity.HistoryEntity
import com.jeager22.nonton.data.entity.SearchHistoryEntity
import com.jeager22.nonton.service.PlaybackService
import com.jeager22.nonton.proxy.ProxyManager
import com.jeager22.nonton.util.DownloadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * MainActivity — single activity hybrid WebView.
 *
 * Arsitektur:
 *  - WebView: render UI lokal dari assets/app/index.html
 *  - ExoPlayer (native): playback video stream URL dari Invidious
 *  - OkHttp (native): semua HTTP ke Invidious (bypass CORS)
 *  - Bridge: JS <-> Kotlin via AndroidBridge.sendCommand + window.receiveData
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var miniPlayer: View
    private lateinit var api: InvidiousApi
    private lateinit var license: LicenseManager
    private lateinit var db: AppDatabase
    private lateinit var downloads: DownloadHelper
    private lateinit var proxyManager: ProxyManager
    private lateinit var streamExtractor: StreamExtractor

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentVideo: JSONObject? = null
    private var currentQuality: String = "720p"
    private var savedBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var savedVolume: Float = 1f

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        proxyManager = ProxyManager(this)
        api = InvidiousApi(proxyManager.engine)
        streamExtractor = StreamExtractor(api, proxyManager.engine)
        license = LicenseManager(this)
        downloads = DownloadHelper(this)
        db = App.database

        // --- UI references ---
        web = findViewById(R.id.web)
        playerView = findViewById(R.id.player)
        miniPlayer = findViewById(R.id.miniPlayer)

        // --- ExoPlayer setup ---
        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    send("playbackState", JSONObject().put("isPlaying", isPlaying)
                        .put("position", currentPosition).put("duration", duration).toString())
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    send("playbackState", JSONObject().put("isPlaying", isPlaying)
                        .put("position", currentPosition).put("duration", duration).toString())
                }
            })
        }
        playerView.player = player
        playerView.useController = false // custom WebView controls
        setupGestures()

        // --- WebView setup ---
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webViewClient = WebViewClient()
        web.loadUrl("file:///android_asset/app/index.html")

        // --- Header wiring ---
        findViewById<android.widget.EditText>(R.id.nativeSearch)
            .setOnEditorActionListener { v, _, _ ->
                val q = v.text.toString().trim()
                if (q.isNotEmpty()) {
                    scope.launch {
                        db.searchHistoryDao().insert(
                            SearchHistoryEntity(query = q, createdAt = System.currentTimeMillis())
                        )
                        db.searchHistoryDao().trim()
                    }
                    // Call API directly and forward results to WebView
                    thread {
                        try {
                            val raw = api.search(q)
                            send("search", raw)
                        } catch (e: Exception) {
                            send("error", JSONObject().put("message", e.message ?: "Search error")
                                .put("key", "search").toString())
                        }
                    }
                }
                true
            }
        findViewById<android.widget.TextView>(R.id.license).setOnClickListener {
            send("license", license.info().toString())
        }
        findViewById<android.widget.TextView>(R.id.settings).setOnClickListener {
            send("proxyStatus", proxyManager.engine.statusJson("Proxy Matrix ready").toString())
            send("openProxy", "{}")
        }

        // --- Cek expired saat start ---
        if (license.isExpired()) {
            handler.postDelayed({ send("license", license.info().toString()) }, 600)
        }

        // --- Search history saat fokus ---
        DownloadHelper.register(this)
    }

    /** Gestur pada PlayerView: double-tap seek, swipe brightness/volume. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val width = playerView.width
                val x = e.x.toInt()
                val pos = player.currentPosition
                player.seekTo(if (x < width / 2) pos - 10_000 else pos + 10_000)
                return true
            }
        })
        playerView.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            // Vertical swipe: left=brightness, right=volume (basic)
            if (ev.action == MotionEvent.ACTION_MOVE && ev.historySize > 0) {
                val dy = ev.getHistoricalY(0) - ev.y
                val width = playerView.width
                if (dy > 30) {
                    if (ev.x < width / 2) adjustBrightness(-0.05f)
                    else adjustVolume(-0.05f)
                } else if (dy < -30) {
                    if (ev.x < width / 2) adjustBrightness(0.05f)
                    else adjustVolume(0.05f)
                }
            }
            true
        }
    }

    private fun adjustBrightness(delta: Float) {
        val lp = window.attributes
        val cur = if (lp.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) 0.5f
                  else lp.screenBrightness
        lp.screenBrightness = (cur + delta).coerceIn(0.05f, 1f)
        window.attributes = lp
    }

    private fun adjustVolume(delta: Float) {
        val am = getSystemService(android.media.AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        am.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            (cur + (delta * max).toInt()).coerceIn(0, max), 0
        )
    }

    /** Kirim data ke WebView: window.receiveData(key, json). */
    private fun send(key: String, json: String) {
        handler.post {
            web.evaluateJavascript(
                "window.receiveData(${JSONObject.quote(key)}, $json)", null
            )
        }
    }

    /** Bridge: WebView -> Kotlin. */
    inner class Bridge {
        @JavascriptInterface
        fun sendCommand(cmd: String, payload: String) {
            // Blokir cmd selain lisensi jika expired
            if (license.isExpired() && cmd !in allowedWhenExpired) return

            when (cmd) {
                "trending"   -> {
                    // Payload format: "REGION" atau "REGION|TYPE" (e.g. "ID|music")
                    val parts = payload.split("|")
                    val region = parts.getOrNull(0)?.ifBlank { "ID" } ?: "ID"
                    val type = parts.getOrNull(1)?.ifBlank { null }
                    network("trending") { api.trending(region, type) }
                }
                "music"      -> network("music") { api.trending("ID", "music") }
                "search"     -> {
                    val q = try { JSONObject(payload).optString("q", payload) } catch (_: Exception) { payload }
                    network("search") { api.search(q) }
                }
                "watch"      -> network("video") { api.video(payload) }
                "activate"   -> {
                    val ok = license.activate(payload)
                    send("activation", JSONObject().put("ok", ok).put("info", license.info()).toString())
                }
                "play"       -> play(payload)
                "pause"      -> player.pause()
                "resume"     -> player.play()
                "seek"       -> player.seekTo(payload.toLong())
                "setQuality" -> { currentQuality = payload; rebuildPlayer() }
                "pip"        -> enterPip()
                "fullscreen" -> toggleFullscreen(payload == "true")
                "showMini"   -> showMiniPlayer()
                "hideMini"   -> hideMiniPlayer()
                "favorite"   -> toggleFavorite(payload)
                "unfavorite" -> removeFavorite(payload)
                "listFavorites" -> listFavorites()
                "listHistory"   -> listHistory()
                "clearHistory"  -> clearHistory()
                "deleteHistory" -> deleteHistoryItem(payload)
                "searchHistory" -> pushSearchHistory()
                "clearSearchHistory" -> clearSearchHistory()
                "download"   -> startDownload(payload)
                "proxyStatus" -> send("proxyStatus", proxyManager.engine.statusJson("Proxy Matrix ready").toString())
                "proxyGodMode" -> proxyGodMode()
                "proxyAutoBest" -> proxyAutoBest()
                "proxyScan" -> proxyScan()
                "proxyStealth" -> send("proxyStatus", proxyManager.engine.setStealth(payload).toString())
                "proxyRotate" -> send("proxyStatus", proxyManager.engine.setAutoRotate(payload == "true").toString())
                "proxyImport" -> send("proxyStatus", proxyManager.engine.importCustom(payload).toString())
                "proxyDirect" -> send("proxyStatus", proxyManager.engine.emergencyDirect().toString())
                "proxyClearBlacklist" -> send("proxyStatus", proxyManager.engine.clearBlacklist().toString())
                "back"       -> finish()
            }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            handler.post { android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    private val allowedWhenExpired = setOf("activate", "license", "proxyStatus", "proxyDirect")

    private fun network(key: String, block: () -> String) = thread {
        try {
            val raw = block()
            // Untuk trending/music/search: kirim raw JSON ke WebView.
            // Untuk "video": simpan current + push history + kirim dengan tambahan info favorit.
            if (key == "video") {
                val o = JSONObject(raw)
                currentVideo = o
                val id = o.optString("videoId")
                if (id.isNotEmpty()) {
                    scope.launch {
                        db.historyDao().insert(
                            HistoryEntity(
                                videoId = id,
                                title = o.optString("title"),
                                author = o.optString("author"),
                                authorId = o.optString("authorId"),
                                thumbnailUrl = o.optJSONArray("videoThumbnails")?.optJSONObject(0)?.optString("url") ?: "",
                                lengthSeconds = o.optInt("lengthSeconds"),
                                watchedAt = System.currentTimeMillis()
                            )
                        )
                        db.historyDao().trim()
                    }
                }
            }
            send(key, raw)
        } catch (e: Exception) {
            send("error", JSONObject().put("message", e.message ?: "Network error").put("key", key).toString())
        }
    }

    /** Mulai playback with StreamExtractor + SmartProxy failover. */
    private fun play(payload: String) {
        thread {
            try {
                val o = if (payload.startsWith("{")) JSONObject(payload) else currentVideo ?: return@thread
                currentVideo = o
                val result = streamExtractor.extract(o, currentQuality)
                if (result == null) {
                    send("error", JSONObject().put("message", "No stream URL after NewPipe-style + Piped/Invidious fallback").toString())
                    return@thread
                }
                send("streamStatus", JSONObject()
                    .put("source", result.source)
                    .put("quality", result.quality)
                    .put("proxy", proxyManager.engine.statusJson().opt("active") ?: JSONObject.NULL)
                    .toString())
                handler.post {
                    playerView.visibility = View.VISIBLE
                    player.setMediaItem(MediaItem.fromUri(result.url))
                    player.prepare()
                    player.play()
                    val svc = Intent(this, PlaybackService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc)
                    else startService(svc)
                }
            } catch (e: Exception) {
                send("error", JSONObject().put("message", e.message ?: "Play error").toString())
            }
        }
    }

    private fun proxyLog(message: String) {
        send("proxyLog", JSONObject().put("message", message).put("time", System.currentTimeMillis()).toString())
    }

    private fun proxyGodMode() = thread {
        try {
            val status = proxyManager.engine.godMode { proxyLog(it) }
            send("proxyStatus", status.toString())
        } catch (e: Exception) {
            send("proxyStatus", proxyManager.engine.statusJson("GOD MODE error: ${e.message}").toString())
        }
    }

    private fun proxyAutoBest() = thread {
        try {
            val status = proxyManager.engine.autoBest { proxyLog(it) }
            send("proxyStatus", status.toString())
        } catch (e: Exception) {
            send("proxyStatus", proxyManager.engine.statusJson("AUTO BEST error: ${e.message}").toString())
        }
    }

    private fun proxyScan() = thread {
        try {
            val status = proxyManager.engine.scanFree { proxyLog(it) }
            send("proxyStatus", status.toString())
        } catch (e: Exception) {
            send("proxyStatus", proxyManager.engine.statusJson("SCAN error: ${e.message}").toString())
        }
    }

    private fun rebuildPlayer() {
        currentVideo?.let { play(it.toString()) }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && player.isPlaying) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun toggleFullscreen(enter: Boolean) {
        val lp = window.attributes
        if (enter) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            playerView.layoutParams = playerView.layoutParams.apply {
                height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            playerView.layoutParams = playerView.layoutParams.apply {
                height = (resources.displayMetrics.widthPixels * 9 / 16)
            }
        }
    }

    private fun showMiniPlayer() {
        handler.post {
            miniPlayer.visibility = View.VISIBLE
            playerView.visibility = View.GONE
        }
    }

    private fun hideMiniPlayer() {
        handler.post {
            miniPlayer.visibility = View.GONE
        }
    }

    // --- Favorites / History (Room) ---

    private fun toggleFavorite(payload: String) {
        try {
            val o = JSONObject(payload)
            val id = o.optString("videoId")
            scope.launch {
                if (db.favoriteDao().isFavorite(id)) {
                    db.favoriteDao().delete(id)
                } else {
                    db.favoriteDao().upsert(
                        FavoriteEntity(
                            videoId = id,
                            title = o.optString("title"),
                            author = o.optString("author"),
                            authorId = o.optString("authorId"),
                            thumbnailUrl = o.optJSONArray("videoThumbnails")?.optJSONObject(0)?.optString("url") ?: "",
                            lengthSeconds = o.optInt("lengthSeconds"),
                            addedAt = System.currentTimeMillis()
                        )
                    )
                }
                send("favoriteToggle", JSONObject().put("videoId", id).put("isFavorite", db.favoriteDao().isFavorite(id)).toString())
            }
        } catch (_: Exception) {}
    }

    private fun removeFavorite(id: String) {
        scope.launch {
            db.favoriteDao().delete(id)
            listFavorites()
        }
    }

    private fun listFavorites() {
        scope.launch {
            val list = db.favoriteDao().getAll()
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().apply {
                put("videoId", it.videoId); put("title", it.title); put("author", it.author)
                put("videoThumbnails", JSONArray().put(JSONObject().put("url", it.thumbnailUrl)))
                put("lengthSeconds", it.lengthSeconds); put("isFavorite", true)
            })}
            send("favorites", arr.toString())
        }
    }

    private fun listHistory() {
        scope.launch {
            val list = db.historyDao().getRecent()
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().apply {
                put("videoId", it.videoId); put("title", it.title); put("author", it.author)
                put("videoThumbnails", JSONArray().put(JSONObject().put("url", it.thumbnailUrl)))
                put("lengthSeconds", it.lengthSeconds); put("watchedAt", it.watchedAt)
            })}
            send("history", arr.toString())
        }
    }

    private fun clearHistory() { scope.launch { db.historyDao().clear(); listHistory() } }
    private fun deleteHistoryItem(id: String) { scope.launch { db.historyDao().deleteByVideoId(id); listHistory() } }

    private fun pushSearchHistory() {
        scope.launch {
            val list = db.searchHistoryDao().getAll()
            val arr = JSONArray()
            list.forEach { arr.put(it.query) }
            send("searchHistory", arr.toString())
        }
    }

    private fun clearSearchHistory() {
        scope.launch { db.searchHistoryDao().clear(); pushSearchHistory() }
    }

    private fun startDownload(payload: String) {
        try {
            val o = JSONObject(payload)
            val isAudio = o.optBoolean("audio", false)
            val title = o.optString("title", "video")
            val formats = (currentVideo ?: return).optJSONArray("adaptiveFormats") ?: JSONArray()
            var chosen: JSONObject? = null
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val type = f.optString("type", "")
                if (isAudio && type.startsWith("audio/")) { chosen = f; break }
                if (!isAudio && type.startsWith("video/")) {
                    if (f.optString("qualityLabel") == o.optString("quality", "720p")) { chosen = f; break }
                    if (chosen == null) chosen = f
                }
            }
            if (chosen != null) downloads.start(chosen!!, title, isAudio)
            else handler.post { android.widget.Toast.makeText(this, "Stream tidak tersedia", android.widget.Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            handler.post { android.widget.Toast.makeText(this, "Download error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    // --- Lifecycle: PiP & background ---

    override fun onUserLeaveHint() {
        if (player.isPlaying && Build.VERSION.SDK_INT >= 26) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            send("pipEntered", "{}")
        } else {
            send("pipExited", "{}")
        }
    }

    override fun onBackPressed() {
        if (player.isPlaying && Build.VERSION.SDK_INT >= 26) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } else if (web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        stopService(Intent(this, PlaybackService::class.java))
    }
}

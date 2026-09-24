package xyz.mpv.rex.tv.server

import android.content.Context
import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import xyz.mpv.rex.tv.model.TvPlaybackState
import xyz.mpv.rex.tv.model.TvStreamItem
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Ultra-low latency Hotspot & LAN HTTP Media Streaming Server for TV.
 * Streams local phone videos directly to any Smart TV browser, Apple TV, Android TV,
 * FireTV, or PC without Chromecast latency or cloud transcoding.
 */
class HotspotTvMediaServer(
    private val context: Context,
    val serverPort: Int = 8765
) : NanoHTTPD("0.0.0.0", serverPort) {

    companion object {
        private const val TAG = "HotspotTvMediaServer"

        @Volatile
        private var instance: HotspotTvMediaServer? = null

        fun getInstance(context: Context, port: Int = 8765): HotspotTvMediaServer {
            return instance ?: synchronized(this) {
                instance ?: HotspotTvMediaServer(context.applicationContext, port).also {
                    instance = it
                }
            }
        }

        fun isRunning(): Boolean = instance?.isAlive == true
    }

    private val _playbackState = MutableStateFlow(TvPlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val _currentStreamItem = MutableStateFlow<TvStreamItem?>(null)
    val currentStreamItem = _currentStreamItem.asStateFlow()

    private val _libraryVideos = MutableStateFlow<List<TvStreamItem>>(emptyList())
    val libraryVideos = _libraryVideos.asStateFlow()

    // Command queue dispatched from phone to TV web player
    private val pendingCommands = ConcurrentLinkedQueue<JSONObject>()

    fun setLibrary(videos: List<TvStreamItem>) {
        _libraryVideos.value = videos
    }

    fun setStreamItem(item: TvStreamItem) {
        _currentStreamItem.value = item
        _playbackState.value = _playbackState.value.copy(
            activeItem = item,
            currentPositionMs = 0L,
            isPlaying = true
        )
        // Send load command to TV
        sendCommand("load", JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("subtitle", item.subtitle)
            put("url", "/stream")
            put("poster", item.posterUrl ?: "")
        })
    }

    fun sendCommand(action: String, payload: Any? = null) {
        val cmd = JSONObject().apply {
            put("action", action)
            put("timestamp", System.currentTimeMillis())
            if (payload != null) {
                put("payload", payload)
            }
        }
        pendingCommands.offer(cmd)
        
        // Update local state projection
        when (action) {
            "play" -> _playbackState.value = _playbackState.value.copy(isPlaying = true)
            "pause" -> _playbackState.value = _playbackState.value.copy(isPlaying = false)
            "seek" -> if (payload is Long) _playbackState.value = _playbackState.value.copy(currentPositionMs = payload)
            "volume" -> if (payload is Number) _playbackState.value = _playbackState.value.copy(volume = payload.toFloat())
        }
    }

    fun startServer(): Boolean {
        return try {
            if (!isAlive) {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.d(TAG, "Hotspot TV Media Server started on port $serverPort")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TV server on port $serverPort", e)
            false
        }
    }

    fun stopServer() {
        try {
            if (isAlive) {
                stop()
                Log.d(TAG, "Hotspot TV Media Server stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TV server", e)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            uri == "/" || uri == "/tv" -> serveTvWebPlayer()
            uri == "/stream" -> serveMediaStream(session)
            uri == "/api/status" && method == Method.GET -> serveStatusJson()
            uri == "/api/status" && method == Method.POST -> handleStatusUpdate(session)
            uri == "/api/poll-command" -> handleCommandPoll()
            uri == "/api/control" && method == Method.POST -> handlePhoneControl(session)
            uri == "/api/playlist" -> servePlaylistJson()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }

    private fun serveTvWebPlayer(): Response {
        val active = _currentStreamItem.value
        val title = active?.title ?: "MaxStream Hotspot Player"
        val subtitle = active?.subtitle ?: "Ready to Play"
        val poster = active?.posterUrl ?: ""

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>MaxStream TV Player</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background: #07080B;
                        color: #F9FAFB;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        overflow: hidden;
                        height: 100vh;
                        width: 100vw;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                    #video-container {
                        position: relative;
                        width: 100vw;
                        height: 100vh;
                        background: #000;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                    video {
                        width: 100%;
                        height: 100%;
                        object-fit: contain;
                        outline: none;
                    }
                    .glass-panel {
                        background: rgba(12, 15, 23, 0.75);
                        backdrop-filter: blur(24px);
                        -webkit-backdrop-filter: blur(24px);
                        border: 1px solid rgba(255, 255, 255, 0.15);
                        border-radius: 20px;
                        box-shadow: 0 20px 50px rgba(0,0,0,0.8), inset 0 1px 0 rgba(255,255,255,0.2);
                    }
                    #hud-overlay {
                        position: absolute;
                        bottom: 40px;
                        left: 50%;
                        transform: translateX(-50%);
                        width: 85%;
                        max-width: 1200px;
                        padding: 24px 32px;
                        transition: opacity 0.4s ease, transform 0.4s ease;
                        opacity: 0;
                        pointer-events: none;
                        z-index: 50;
                    }
                    #hud-overlay.visible {
                        opacity: 1;
                        pointer-events: auto;
                        transform: translateX(-50%) translateY(0);
                    }
                    .header-info {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 16px;
                    }
                    .badge {
                        background: #FF2A55;
                        color: white;
                        font-size: 11px;
                        font-weight: 800;
                        padding: 4px 10px;
                        border-radius: 6px;
                        letter-spacing: 1px;
                        text-transform: uppercase;
                        box-shadow: 0 0 12px rgba(255, 42, 85, 0.6);
                    }
                    .media-title {
                        font-size: 24px;
                        font-weight: 700;
                        color: #FFF;
                        text-shadow: 0 2px 8px rgba(0,0,0,0.8);
                    }
                    .media-subtitle {
                        font-size: 14px;
                        color: #94A3B8;
                        margin-top: 2px;
                    }
                    .progress-bar-container {
                        width: 100%;
                        height: 8px;
                        background: rgba(255, 255, 255, 0.2);
                        border-radius: 4px;
                        cursor: pointer;
                        position: relative;
                        overflow: hidden;
                    }
                    .progress-fill {
                        height: 100%;
                        width: 0%;
                        background: linear-gradient(90deg, #FF2A55, #38BDF8);
                        border-radius: 4px;
                        transition: width 0.1s linear;
                        box-shadow: 0 0 10px rgba(56, 189, 248, 0.7);
                    }
                    .time-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 13px;
                        color: #CBD5E1;
                        font-weight: 600;
                        margin-top: 8px;
                    }
                    #status-toast {
                        position: absolute;
                        top: 32px;
                        right: 32px;
                        padding: 12px 20px;
                        font-size: 14px;
                        font-weight: 600;
                        color: #38BDF8;
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        opacity: 0;
                        transition: opacity 0.3s ease;
                        z-index: 60;
                    }
                    #status-toast.visible { opacity: 1; }
                    .center-prompt {
                        position: absolute;
                        text-align: center;
                        z-index: 40;
                        pointer-events: none;
                    }
                    .play-icon-glow {
                        width: 80px;
                        height: 80px;
                        background: rgba(255, 42, 85, 0.9);
                        border-radius: 50%;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        margin: 0 auto 16px;
                        box-shadow: 0 0 30px rgba(255, 42, 85, 0.8);
                    }
                </style>
            </head>
            <body>
                <div id="video-container">
                    <video id="player" autoplay playsinline preload="auto" poster="$poster">
                        <source src="/stream" type="video/mp4">
                        Your TV browser does not support HTML5 video.
                    </video>

                    <div id="status-toast" class="glass-panel">
                        <span style="display:inline-block; width:8px; height:8px; border-radius:50%; background:#38BDF8; box-shadow:0 0 8px #38BDF8;"></span>
                        <span id="toast-text">Hotspot Direct Zero-Lag Stream</span>
                    </div>

                    <div id="hud-overlay" class="glass-panel">
                        <div class="header-info">
                            <div>
                                <div class="badge">MAXSTREAM DIRECT STREAM</div>
                                <div class="media-title" id="hud-title">$title</div>
                                <div class="media-subtitle" id="hud-subtitle">$subtitle</div>
                            </div>
                            <div style="text-align: right; color: #94A3B8; font-size: 12px;">
                                Press <b>[OK]</b> Play/Pause &bull; <b>[&larr; &rarr;]</b> Seek &bull; <b>[F]</b> Fullscreen
                            </div>
                        </div>
                        <div class="progress-bar-container" id="prog-bar">
                            <div class="progress-fill" id="prog-fill"></div>
                        </div>
                        <div class="time-row">
                            <span id="curr-time">00:00</span>
                            <span id="dur-time">00:00</span>
                        </div>
                    </div>
                </div>

                <script>
                    const video = document.getElementById('player');
                    const hud = document.getElementById('hud-overlay');
                    const progFill = document.getElementById('prog-fill');
                    const currTimeEl = document.getElementById('curr-time');
                    const durTimeEl = document.getElementById('dur-time');
                    const toast = document.getElementById('status-toast');
                    const toastText = document.getElementById('toast-text');
                    const hudTitle = document.getElementById('hud-title');
                    const hudSubtitle = document.getElementById('hud-subtitle');

                    let hudTimeout;
                    function showHud(ms = 4000) {
                        hud.classList.add('visible');
                        clearTimeout(hudTimeout);
                        hudTimeout = setTimeout(() => {
                            if (!video.paused) {
                                hud.classList.remove('visible');
                            }
                        }, ms);
                    }

                    function showToast(msg, duration = 3000) {
                        toastText.textContent = msg;
                        toast.classList.add('visible');
                        setTimeout(() => toast.classList.remove('visible'), duration);
                    }

                    function formatTime(secs) {
                        if (isNaN(secs) || secs < 0) return "00:00";
                        const h = Math.floor(secs / 3600);
                        const m = Math.floor((secs % 3600) / 60);
                        const s = Math.floor(secs % 60);
                        const padM = m < 10 ? '0' + m : m;
                        const padS = s < 10 ? '0' + s : s;
                        if (h > 0) {
                            return h + ':' + padM + ':' + padS;
                        }
                        return padM + ':' + padS;
                    }

                    video.addEventListener('timeupdate', () => {
                        if (video.duration) {
                            const pct = (video.currentTime / video.duration) * 100;
                            progFill.style.width = pct + '%';
                            currTimeEl.textContent = formatTime(video.currentTime);
                            durTimeEl.textContent = formatTime(video.duration);
                        }
                    });

                    video.addEventListener('play', () => {
                        showToast("Playing");
                        showHud(2500);
                        sendState();
                    });

                    video.addEventListener('pause', () => {
                        showToast("Paused");
                        showHud(100000);
                        sendState();
                    });

                    // Key navigation for TV Remotes & Keyboards
                    window.addEventListener('keydown', (e) => {
                        showHud();
                        switch(e.key) {
                            case ' ':
                            case 'Enter':
                            case 'MediaPlayPause':
                                if (video.paused) video.play(); else video.pause();
                                break;
                            case 'ArrowRight':
                            case 'MediaFastForward':
                                video.currentTime = Math.min(video.duration, video.currentTime + 10);
                                showToast("+10s");
                                break;
                            case 'ArrowLeft':
                            case 'MediaRewind':
                                video.currentTime = Math.max(0, video.currentTime - 10);
                                showToast("-10s");
                                break;
                            case 'ArrowUp':
                                video.volume = Math.min(1, video.volume + 0.1);
                                showToast("Volume: " + Math.round(video.volume * 100) + "%");
                                break;
                            case 'ArrowDown':
                                video.volume = Math.max(0, video.volume - 0.1);
                                showToast("Volume: " + Math.round(video.volume * 100) + "%");
                                break;
                            case 'f':
                            case 'F':
                                if (!document.fullscreenElement) {
                                    document.documentElement.requestFullscreen().catch(() => {});
                                } else {
                                    document.exitFullscreen().catch(() => {});
                                }
                                break;
                        }
                    });

                    // Continuous command polling loop from phone
                    async function pollCommands() {
                        try {
                            const resp = await fetch('/api/poll-command');
                            if (resp.ok) {
                                const data = await resp.json();
                                if (data && data.action) {
                                    handleCommand(data);
                                }
                            }
                        } catch (e) {
                            // network delay
                        }
                        setTimeout(pollCommands, 600);
                    }

                    function handleCommand(cmd) {
                        showHud();
                        switch (cmd.action) {
                            case 'play':
                                video.play().catch(() => {});
                                break;
                            case 'pause':
                                video.pause();
                                break;
                            case 'seek':
                                if (cmd.payload !== undefined) {
                                    video.currentTime = Number(cmd.payload) / 1000.0;
                                }
                                break;
                            case 'volume':
                                if (cmd.payload !== undefined) {
                                    video.volume = Math.max(0, Math.min(1, Number(cmd.payload)));
                                }
                                break;
                            case 'fullscreen':
                                if (!document.fullscreenElement) {
                                    document.documentElement.requestFullscreen().catch(() => {});
                                }
                                break;
                            case 'load':
                                if (cmd.payload) {
                                    if (cmd.payload.title) hudTitle.textContent = cmd.payload.title;
                                    if (cmd.payload.subtitle) hudSubtitle.textContent = cmd.payload.subtitle;
                                    if (cmd.payload.url) {
                                        video.src = cmd.payload.url + '?t=' + Date.now();
                                        video.load();
                                        video.play().catch(() => {});
                                    }
                                }
                                break;
                        }
                        sendState();
                    }

                    async function sendState() {
                        try {
                            await fetch('/api/status', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({
                                    currentTimeMs: Math.floor(video.currentTime * 1000),
                                    durationMs: Math.floor((video.duration || 0) * 1000),
                                    isPlaying: !video.paused,
                                    volume: video.volume,
                                    isMuted: video.muted
                                })
                            });
                        } catch (e) {}
                    }

                    // Periodic state sync
                    setInterval(sendState, 2000);
                    pollCommands();
                    showHud(4000);
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
    }

    private fun serveMediaStream(session: IHTTPSession): Response {
        val activeItem = _currentStreamItem.value
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No active video stream")

        return try {
            val rangeHeader = session.headers["range"]
            var totalLength = activeItem.fileSize

            val inputStream: InputStream = if (activeItem.isLocalFile) {
                if (activeItem.uriString.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(activeItem.uriString))
                        ?: FileInputStream(File(activeItem.uriString))
                } else {
                    val file = File(activeItem.uriString.removePrefix("file://"))
                    if (totalLength <= 0L && file.exists()) {
                        totalLength = file.length()
                    }
                    FileInputStream(file)
                }
            } else {
                // If remote video link, forward/stream directly
                val url = java.net.URL(activeItem.uriString)
                val conn = url.openConnection()
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                if (totalLength <= 0L) {
                    totalLength = conn.contentLengthLong
                }
                conn.getInputStream()
            }

            if (totalLength <= 0L) {
                // Return full chunked stream if length is unknown
                val response = newChunkedResponse(Response.Status.OK, activeItem.mimeType, inputStream)
                response.addHeader("Accept-Ranges", "bytes")
                response.addHeader("Access-Control-Allow-Origin", "*")
                return response
            }

            var startFrom = 0L
            var endAt = totalLength - 1

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val rangeValue = rangeHeader.substring(6).trim()
                val minusIndex = rangeValue.indexOf('-')
                if (minusIndex >= 0) {
                    val startStr = rangeValue.substring(0, minusIndex).trim()
                    val endStr = rangeValue.substring(minusIndex + 1).trim()

                    if (startStr.isNotEmpty()) {
                        startFrom = startStr.toLongOrNull() ?: 0L
                    }
                    if (endStr.isNotEmpty()) {
                        endAt = endStr.toLongOrNull() ?: (totalLength - 1)
                    }
                }
            }

            if (startFrom >= totalLength) {
                val resp = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                resp.addHeader("Content-Range", "bytes */$totalLength")
                return resp
            }

            if (startFrom > 0) {
                var skipped = 0L
                while (skipped < startFrom) {
                    val count = inputStream.skip(startFrom - skipped)
                    if (count <= 0) break
                    skipped += count
                }
            }

            val contentLength = endAt - startFrom + 1
            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                activeItem.mimeType,
                inputStream,
                contentLength
            )
            response.addHeader("Content-Range", "bytes $startFrom-$endAt/$totalLength")
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming media: ${e.message}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error streaming media: ${e.message}")
        }
    }

    private fun handleCommandPoll(): Response {
        val nextCmd = pendingCommands.poll()
        val json = nextCmd?.toString() ?: "{}"
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun handlePhoneControl(session: IHTTPSession): Response {
        try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val body = map["postData"] ?: ""
            if (body.isNotEmpty()) {
                val obj = JSONObject(body)
                val action = obj.optString("action")
                val payload = obj.opt("payload")
                sendCommand(action, payload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling phone control: ${e.message}")
        }
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun handleStatusUpdate(session: IHTTPSession): Response {
        try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val body = map["postData"] ?: ""
            if (body.isNotEmpty()) {
                val obj = JSONObject(body)
                val curr = obj.optLong("currentTimeMs", 0L)
                val dur = obj.optLong("durationMs", 0L)
                val isPlaying = obj.optBoolean("isPlaying", false)
                val vol = obj.optDouble("volume", 1.0).toFloat()
                val muted = obj.optBoolean("isMuted", false)

                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = curr,
                    durationMs = if (dur > 0L) dur else _playbackState.value.durationMs,
                    isPlaying = isPlaying,
                    volume = vol,
                    isMuted = muted
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status: ${e.message}")
        }
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun serveStatusJson(): Response {
        val state = _playbackState.value
        val json = JSONObject().apply {
            put("isPlaying", state.isPlaying)
            put("currentPositionMs", state.currentPositionMs)
            put("durationMs", state.durationMs)
            put("volume", state.volume)
            put("isMuted", state.isMuted)
            put("activeTitle", state.activeItem?.title ?: "")
        }.toString()

        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun servePlaylistJson(): Response {
        val array = JSONArray()
        for (item in _libraryVideos.value) {
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("durationMs", item.durationMs)
                put("mimeType", item.mimeType)
            })
        }
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }
}

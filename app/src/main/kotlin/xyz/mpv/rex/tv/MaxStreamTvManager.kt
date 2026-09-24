package xyz.mpv.rex.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.tv.model.TvStreamItem
import xyz.mpv.rex.tv.server.HotspotNetworkHelper
import xyz.mpv.rex.tv.server.HotspotTvMediaServer

/**
 * Singleton coordinator for MaxStream TV UI, Hotspot Direct Streaming, and TV Remote Control.
 */
object MaxStreamTvManager {

    var isBottomSheetVisible by mutableStateOf(false)
        private set

    private val _activeItem = MutableStateFlow<TvStreamItem?>(null)
    val activeItem = _activeItem.asStateFlow()

    private val _networkInfo = MutableStateFlow<HotspotNetworkHelper.NetworkInfo?>(null)
    val networkInfo = _networkInfo.asStateFlow()

    private var tvServer: HotspotTvMediaServer? = null

    fun initialize(context: Context) {
        if (tvServer == null) {
            tvServer = HotspotTvMediaServer.getInstance(context)
        }
        refreshNetwork(context)
    }

    fun refreshNetwork(context: Context) {
        val info = HotspotNetworkHelper.getLocalNetworkInfo(context)
        _networkInfo.value = info
    }

    fun playOnTv(context: Context, video: Video) {
        val item = TvStreamItem(
            id = video.id.toString(),
            title = video.title.ifEmpty { video.displayName },
            subtitle = "${video.resolution} • ${video.durationFormatted} • ${video.sizeFormatted}",
            durationMs = video.duration,
            uriString = video.uri.toString(),
            mimeType = video.mimeType.ifEmpty { "video/mp4" },
            isLocalFile = true,
            fileSize = video.size
        )
        playOnTv(context, item)
    }

    fun playOnTv(context: Context, item: TvStreamItem) {
        initialize(context)
        _activeItem.value = item

        val server = tvServer ?: HotspotTvMediaServer.getInstance(context).also { tvServer = it }
        val started = server.startServer()

        server.setStreamItem(item)
        refreshNetwork(context)
        isBottomSheetVisible = true

        if (started) {
            val net = _networkInfo.value
            val isHotspot = net?.isHotspot == true
            val msg = if (isHotspot) {
                "Hotspot TV Stream Ready! Open TV browser to: ${net?.tvWebUrl}"
            } else {
                "TV Stream Ready on local Wi-Fi: ${net?.tvWebUrl}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun hideBottomSheet() {
        isBottomSheetVisible = false
    }

    fun sendTvCommand(action: String, payload: Any? = null) {
        tvServer?.sendCommand(action, payload)
    }

    fun getServer(): HotspotTvMediaServer? = tvServer

    fun openTvWebInBrowser(context: Context) {
        val url = _networkInfo.value?.tvWebUrl ?: "http://127.0.0.1:8765/tv"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

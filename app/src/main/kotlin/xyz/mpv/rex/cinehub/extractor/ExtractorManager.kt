package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack
import java.util.concurrent.TimeUnit

/**
 * Orchestrates extractor registration, resolution, redirect unwrapping, and callback emission.
 */
object ExtractorManager {
    private const val TAG = "ExtractorManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val extractors = mutableListOf<ExtractorApi>()

    init {
        register(StreamWishExtractor(httpClient))
        register(FilemoonExtractor(httpClient))
        register(DoodStreamExtractor(httpClient))
        register(StreamTapeExtractor(httpClient))
        register(Mp4UploadExtractor(httpClient))
        register(MixDropExtractor(httpClient))
        register(JWPlayerExtractor(httpClient))
        register(GenericM3u8Extractor(httpClient))
        register(GenericEmbedExtractor(httpClient))
    }

    fun register(extractor: ExtractorApi) {
        extractors.add(0, extractor)
    }

    fun canExtract(url: String): Boolean {
        return extractors.any { it.canExtract(url) }
    }

    suspend fun loadExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)? = null,
        callback: (CineHubStreamLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "[EXTRACTOR] Inspecting URL for extraction: $url")
        var matched = false

        for (extractor in extractors) {
            if (extractor.canExtract(url)) {
                Log.i(TAG, "[EXTRACTOR] Found extractor ${extractor.name} for $url")
                try {
                    extractor.getUrl(url, referer, subtitleCallback, callback)
                    matched = true
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "[EXTRACTOR_ERROR] Extractor ${extractor.name} failed: ${e.message}")
                }
            }
        }

        if (!matched && (url.startsWith("http://") || url.startsWith("https://"))) {
            try {
                val headReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                    .head()
                    .build()
                val headResp = httpClient.newCall(headReq).execute()
                val finalUrl = headResp.request.url.toString()
                if (finalUrl != url) {
                    Log.d(TAG, "[EXTRACTOR] Redirect unwrapped: $url -> $finalUrl")
                    for (extractor in extractors) {
                        if (extractor.canExtract(finalUrl)) {
                            extractor.getUrl(finalUrl, referer ?: url, subtitleCallback, callback)
                            return@withContext true
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore redirect check error
            }
        }

        matched
    }
}

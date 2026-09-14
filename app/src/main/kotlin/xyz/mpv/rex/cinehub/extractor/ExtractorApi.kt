package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import okhttp3.OkHttpClient

/**
 * Base class for all video extractors.
 * Follows CloudStream ExtractorApi pattern.
 */
abstract class ExtractorApi {
    companion object {
        val defaultClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    abstract val name: String
    abstract val mainUrl: String
    open val requiresReferer: Boolean = false

    abstract fun canExtract(url: String): Boolean

    abstract suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleData) -> Unit = {},
        callback: (ExtractorLinkData) -> Unit
    )
}

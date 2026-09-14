package xyz.mpv.rex.cinehub.extractor

import xyz.mpv.rex.cinehub.extension.api.CineHubStreamLink
import xyz.mpv.rex.cinehub.extension.api.CineHubSubtitleTrack

/**
 * Base abstraction for video host / embed extractors.
 * Modeled after CloudStream's ExtractorApi architecture.
 */
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    open val requiresReferer: Boolean = false

    abstract suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)? = null,
        callback: (CineHubStreamLink) -> Unit
    )

    open fun canExtract(url: String): Boolean {
        val cleanMain = mainUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val cleanUrl = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        return cleanMain.isNotBlank() && (cleanUrl.startsWith(cleanMain) || cleanUrl.contains(cleanMain))
    }
}

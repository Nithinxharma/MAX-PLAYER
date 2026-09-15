package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ExtractorManager coordinates video stream extraction and subtitle discovery
 * across supported hosters and formats.
 */
object ExtractorManager {
    private const val TAG = "CineHub:Extractor"

    private val extractors: List<ExtractorApi> = listOf(
        RabbitstreamExtractor(),
        StreamWishExtractor(),
        VidHideExtractor(),
        FilemoonExtractor(),
        StreamTapeExtractor(),
        MixDropExtractor(),
        DoodExtractor(),
        EmbedExtractor(),
        GenericExtractor()
    )

    fun findExtractor(url: String): ExtractorApi? {
        return extractors.firstOrNull { it.canExtract(url) }
    }

    suspend fun extract(
        url: String,
        referer: String? = null,
        onSubtitle: (SubtitleData) -> Unit = {},
        onLink: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        val extractor = findExtractor(url)
        if (extractor != null) {
            Log.i(TAG, "Routing URL to ${extractor.name}: $url")
            try {
                extractor.getUrl(
                    url = url,
                    referer = referer,
                    subtitleCallback = { sub ->
                        Log.d(TAG, "Discovered subtitle: [${sub.language}] ${sub.url}")
                        onSubtitle(sub)
                    },
                    callback = { link ->
                        Log.d(TAG, "Extracted link: [${link.quality}] ${link.name} -> ${link.url}")
                        onLink(link)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in ${extractor.name} extraction: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "No specific extractor matched for $url, testing GenericExtractor")
            GenericExtractor().getUrl(url, referer, onSubtitle, onLink)
        }
    }
}

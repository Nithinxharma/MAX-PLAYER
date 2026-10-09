package xyz.mpv.rex.cinehub.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import xyz.mpv.rex.RexPlayerActivity

/**
 * RexPlayerBridge handles activity hand-off from CloudStream 3 / CineHub to Max Stream's MPV-based RexPlayerActivity.
 * Serializes the resolved ExtractorLink, HTTP headers, cookies, referer, and subtitles directly into an Android Intent.
 */
object RexPlayerBridge {
    private const val TAG = "RexPlayerBridge"

    /**
     * Primary entry point for launching RexPlayerActivity from CloudStream results or direct extractor resolution.
     */
    fun launchPlayer(
        context: Context,
        title: String? = null,
        episodeName: String? = null,
        link: ExtractorLink,
        subtitles: List<SubtitleFile> = emptyList(),
        posterUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        rating: Double? = null,
        providerName: String? = null,
        allLinks: List<ExtractorLink> = emptyList()
    ) {
        val displayTitle = when {
            !title.isNullOrBlank() && !episodeName.isNullOrBlank() -> "$title - $episodeName"
            !title.isNullOrBlank() -> title
            !episodeName.isNullOrBlank() -> episodeName
            else -> "Stream"
        }

        val intent = Intent(context, RexPlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(link.url)

            // Direct CloudStream playback contract
            putExtra("VIDEO_URL", link.url)
            putExtra("VIDEO_TITLE", displayTitle)
            putExtra("EPISODE_TITLE", episodeName ?: "")
            putExtra("IS_HLS", link.isM3u8 || link.url.contains(".m3u8"))
            putExtra("REFERER", link.referer)

            // Pack Headers into HashMap and String array for both MPVLib and intent listeners
            val headerMap = HashMap<String, String>()
            if (link.referer.isNotBlank()) {
                headerMap["Referer"] = link.referer
            }
            if (link.headers.isNotEmpty()) {
                headerMap.putAll(link.headers)
            }
            if (!headerMap.containsKey("User-Agent") && !headerMap.containsKey("user-agent")) {
                headerMap["User-Agent"] = USER_AGENT
            }
            putExtra("HEADERS", headerMap)

            val headerPairs = mutableListOf<String>()
            headerMap.forEach { (k, v) ->
                headerPairs.add(k)
                headerPairs.add(v)
            }
            putExtra("headers", headerPairs.toTypedArray())

            // Subtitle tracks
            val subUrls = ArrayList(subtitles.map { it.url })
            val subNames = ArrayList(subtitles.map { it.lang })
            putExtra("SUBTITLE_URLS", subUrls)
            putExtra("SUBTITLE_NAMES", subNames)

            // CineTV / REX player metadata contracts
            putExtra("title", displayTitle)
            putExtra("cinetv_title", displayTitle)
            putExtra("filename", displayTitle)
            if (!posterUrl.isNullOrBlank()) putExtra("cinetv_poster", posterUrl)
            if (!overview.isNullOrBlank()) putExtra("cinetv_overview", overview)
            if (!year.isNullOrBlank()) putExtra("cinetv_year", year)
            if (rating != null && rating > 0) putExtra("cinetv_rating", rating.toString())
            if (!providerName.isNullOrBlank()) putExtra("cinetv_provider", providerName)
            putExtra("cinetv_source_type", "cloudstream")

            // Multi-quality link switching
            if (allLinks.isNotEmpty()) {
                putExtra("cinetv_links_urls", allLinks.map { it.url }.toTypedArray())
                putExtra("cinetv_links_names", allLinks.map { l ->
                    xyz.mpv.rex.cinehub.utils.StreamLinkFormatter.formatQualityLanguage(l, providerName ?: "")
                }.toTypedArray())
                putExtra("cinetv_links_qualities", allLinks.map { it.quality }.toIntArray())
                putExtra("cinetv_links_referers", allLinks.map { it.referer }.toTypedArray())
            }

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            // Apply native MPV headers if running in the same process
            applyMpvHeaders(link)
            context.startActivity(intent)
            Log.i(TAG, "Launched RexPlayerActivity for $displayTitle (${link.url})")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch RexPlayerActivity", e)
        }
    }

    /**
     * Injects MPV http-header-fields properties for seamless direct socket stream loading.
     */
    fun applyMpvHeaders(link: ExtractorLink) {
        CloudstreamHeadlessRunner.applyMpvProperties(link)
    }

    /**
     * Backward-compatible overload for existing CineHub callers.
     */
    fun playStream(
        context: Context,
        link: ExtractorLink,
        title: String? = null,
        posterUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        rating: Double? = null,
        providerName: String? = null,
        allLinks: List<ExtractorLink> = emptyList()
    ) {
        launchPlayer(
            context = context,
            title = title,
            episodeName = null,
            link = link,
            subtitles = emptyList(),
            posterUrl = posterUrl,
            overview = overview,
            year = year,
            rating = rating,
            providerName = providerName,
            allLinks = allLinks
        )
    }
}

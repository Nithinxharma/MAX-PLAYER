package xyz.mpv.rex.cinehub.bridge

import android.content.Context
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * RexPlayerBridge provides the clean bridge passing extracted media URLs
 * and HTTP headers (Referer, User-Agent, and custom tokens) to REX-Player.
 */
object RexPlayerBridge {
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
        CloudstreamHeadlessRunner.launchRexPlayer(
            context = context,
            link = link,
            title = title,
            posterUrl = posterUrl,
            overview = overview,
            year = year,
            rating = rating,
            providerName = providerName,
            allLinks = allLinks
        )
    }

    fun applyMpvHeaders(link: ExtractorLink) {
        CloudstreamHeadlessRunner.applyMpvProperties(link)
    }
}

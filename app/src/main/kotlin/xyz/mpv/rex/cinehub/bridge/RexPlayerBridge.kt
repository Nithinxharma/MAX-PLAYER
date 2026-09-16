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
        title: String? = null
    ) {
        CloudstreamHeadlessRunner.launchRexPlayer(context, link, title)
    }

    fun applyMpvHeaders(link: ExtractorLink) {
        CloudstreamHeadlessRunner.applyMpvProperties(link)
    }
}

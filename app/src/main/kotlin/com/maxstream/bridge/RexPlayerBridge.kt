package com.maxstream.bridge

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

data class RexStreamPayload(
    val link: ExtractorLink,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleFile>
)

/**
 * RexPlayerBridge connects CloudStream stream extraction to the host app's REX Player engine.
 */
object RexPlayerBridge {
    private const val TAG = "RexPlayerBridge"

    /**
     * Resolves playable streams and subtitle tracks for the given episode/media token.
     */
    suspend fun resolveStreams(
        providerName: String,
        dataToken: String,
        isCasting: Boolean = false
    ): List<RexStreamPayload> = withContext(Dispatchers.IO) {
        val provider: MainAPI = APIHolder.getApiFromNameNull(providerName) ?: return@withContext emptyList()
        val extractedLinks = CopyOnWriteArrayList<ExtractorLink>()
        val extractedSubs = CopyOnWriteArrayList<SubtitleFile>()

        try {
            // 1. Invoke provider's native loadLinks
            provider.loadLinks(
                data = dataToken,
                isCasting = isCasting,
                subtitleCallback = { sub -> extractedSubs.add(sub) },
                callback = { link -> extractedLinks.add(link) }
            )

            // 2. If dataToken is a direct video host URL and no links were added, try direct Extractor resolution
            if (extractedLinks.isEmpty() && (dataToken.startsWith("http://") || dataToken.startsWith("https://"))) {
                ExtractorManager.loadExtractor(
                    url = dataToken,
                    referer = null,
                    subtitleCallback = { sub -> extractedSubs.add(sub) },
                    callback = { link -> extractedLinks.add(link) }
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving streams on provider $providerName: ${t.message}", t)
        }

        // 3. Transform ExtractorLinks into RexStreamPayload with aggregated headers
        extractedLinks.map { link ->
            val headers = mutableMapOf<String, String>()
            if (link.referer.isNotBlank()) {
                headers["Referer"] = link.referer
            }
            if (link.headers.isNotEmpty()) {
                headers.putAll(link.headers)
            }
            if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
                headers["User-Agent"] = USER_AGENT
            }

            RexStreamPayload(
                link = link,
                headers = headers,
                subtitles = extractedSubs.toList()
            )
        }
    }

    /**
     * Dispatches the extracted stream and metadata directly to REX Player.
     */
    fun playWithRexPlayer(
        context: Context,
        payload: RexStreamPayload,
        title: String? = null,
        posterUrl: String? = null,
        overview: String? = null,
        year: String? = null,
        rating: Double? = null,
        providerName: String? = null,
        allLinks: List<ExtractorLink> = emptyList()
    ) {
        xyz.mpv.rex.cinehub.bridge.CloudstreamHeadlessRunner.launchRexPlayer(
            context = context,
            link = payload.link,
            title = title,
            posterUrl = posterUrl,
            overview = overview,
            year = year,
            rating = rating,
            providerName = providerName,
            allLinks = allLinks
        )
    }
}

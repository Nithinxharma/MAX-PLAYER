package xyz.mpv.rex.maxstream.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.RexPlayerActivity
import xyz.mpv.rex.maxstream.engine.isM3u8
import java.util.concurrent.CopyOnWriteArrayList

data class RexStreamPayload(
    val link: ExtractorLink,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleFile>
)

/**
 * RexPlayerBridge connects CloudStream stream extraction to the host app's REX Player engine.
 * Provides unified stream resolution, HTTP header assembly, failover fallback, and player activity dispatch.
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
        val provider: MainAPI? = APIHolder.getApiFromNameNull(providerName)
        val extractedLinks = CopyOnWriteArrayList<ExtractorLink>()
        val extractedSubs = CopyOnWriteArrayList<SubtitleFile>()

        try {
            // 1. Invoke provider's native loadLinks if provider found
            provider?.loadLinks(
                data = dataToken,
                isCasting = isCasting,
                subtitleCallback = { sub: SubtitleFile -> extractedSubs.add(sub) },
                callback = { link: ExtractorLink -> extractedLinks.add(link) }
            )

            // 2. If dataToken is a direct video host URL and no links were added, try direct Extractor resolution
            if (extractedLinks.isEmpty() && (dataToken.startsWith("http://") || dataToken.startsWith("https://"))) {
                loadExtractor(
                    url = dataToken,
                    referer = "",
                    subtitleCallback = { sub: SubtitleFile -> extractedSubs.add(sub) },
                    callback = { link: ExtractorLink -> extractedLinks.add(link) }
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving streams on provider $providerName: ${t.message}", t)
        }

        // 3. Transform ExtractorLinks into RexStreamPayload with aggregated headers
        extractedLinks.map { link ->
            val headers = HashMap<String, String>()
            if (link.referer.isNotBlank()) {
                headers["Referer"] = link.referer
            }
            if (link.headers.isNotEmpty()) {
                headers.putAll(link.headers)
            }
            if (!headers.containsKey("User-Agent")) {
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
     * Launches RexPlayerActivity with the resolved stream, headers, metadata, and subtitles.
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

            putExtra("VIDEO_URL", link.url)
            putExtra("VIDEO_TITLE", displayTitle)
            putExtra("EPISODE_TITLE", episodeName ?: "")
            putExtra("IS_HLS", link.isM3u8 || link.url.contains(".m3u8"))
            putExtra("REFERER", link.referer)

            val headerMap = HashMap<String, String>()
            if (link.referer.isNotBlank()) {
                headerMap["Referer"] = link.referer
            }
            if (link.headers.isNotEmpty()) {
                headerMap.putAll(link.headers)
            }
            if (!headerMap.containsKey("User-Agent")) {
                headerMap["User-Agent"] = USER_AGENT
            }

            putExtra("EXTRA_HEADERS", headerMap)

            val headerList = headerMap.entries.map { "${it.key}: ${it.value}" }.toTypedArray()
            putExtra("HTTP_HEADERS", headerList)

            if (!subtitles.isNullOrEmpty()) {
                val subUrls = subtitles.map { it.url }.toTypedArray()
                val subNames = subtitles.map { it.lang }.toTypedArray()
                putExtra("SUBTITLE_URLS", subUrls)
                putExtra("SUBTITLE_NAMES", subNames)
            }

            if (!posterUrl.isNullOrBlank()) putExtra("POSTER_URL", posterUrl)
            if (!overview.isNullOrBlank()) putExtra("OVERVIEW", overview)
            if (!year.isNullOrBlank()) putExtra("RELEASE_YEAR", year)
            if (rating != null) putExtra("RATING", rating)
            if (!providerName.isNullOrBlank()) putExtra("PROVIDER_NAME", providerName)

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "Launched RexPlayerActivity for URL: ${link.url}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch RexPlayerActivity: ${t.message}", t)
            Toast.makeText(context, "Error starting playback: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * One-shot resolver and player launcher: fetches streams asynchronously and plays the first healthy link.
     */
    fun resolveAndPlay(
        context: Context,
        providerName: String,
        dataToken: String,
        title: String? = null,
        episodeName: String? = null,
        posterUrl: String? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    ) {
        scope.launch {
            Toast.makeText(context, "Extracting streams from $providerName...", Toast.LENGTH_SHORT).show()
            val payloads = resolveStreams(providerName, dataToken)
            if (payloads.isNotEmpty()) {
                val selected = payloads.first()
                launchPlayer(
                    context = context,
                    title = title,
                    episodeName = episodeName,
                    link = selected.link,
                    subtitles = selected.subtitles,
                    posterUrl = posterUrl,
                    providerName = providerName,
                    allLinks = payloads.map { it.link }
                )
            } else {
                Toast.makeText(context, "No playable streams found for this selection.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

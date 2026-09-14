package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Extractor for Rabbitstream, Megacloud, Dokicloud, and Vidstream embeds.
 */
class RabbitstreamExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "Rabbitstream"
    override val mainUrl: String = "https://rabbitstream.net"
    override val requiresReferer: Boolean = true

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("rabbitstream.") ||
                lower.contains("megacloud.") ||
                lower.contains("dokicloud.") ||
                lower.contains("vidstream.")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val embedId = url.substringAfterLast("/").substringBefore("?")
            val host = "https://" + (java.net.URI(url).host ?: "rabbitstream.net")

            val ajaxUrl = "$host/ajax/embed-4/getSources?id=$embedId"
            val req = Request.Builder()
                .url(ajaxUrl)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .addHeader("Referer", url)
                .build()

            val jsonText = defaultClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            if (jsonText.startsWith("{")) {
                val obj = JSONObject(jsonText)
                val tracks = obj.optJSONArray("tracks")
                if (tracks != null) {
                    for (i in 0 until tracks.length()) {
                        val track = tracks.getJSONObject(i)
                        val kind = track.optString("kind", "")
                        if (kind.contains("captions") || kind.contains("subtitles")) {
                            subtitleCallback(
                                SubtitleData(
                                    language = track.optString("label", "English"),
                                    url = track.optString("file", ""),
                                    isVtt = true
                                )
                            )
                        }
                    }
                }

                val sources = obj.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    val fileUrl = sources.getJSONObject(0).optString("file", "")
                    if (fileUrl.isNotBlank()) {
                        val headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                            "Referer" to "$host/"
                        )
                        val streams = M3u8Helper.generateM3u8(
                            source = name,
                            streamUrl = fileUrl,
                            referer = "$host/",
                            headers = headers,
                            name = name
                        )
                        streams.forEach(callback)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rabbitstream extraction failed for $url: ${e.message}")
        }
    }
}

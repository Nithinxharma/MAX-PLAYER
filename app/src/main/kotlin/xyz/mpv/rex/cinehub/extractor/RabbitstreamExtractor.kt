package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Extractor for Rabbitstream, Megacloud, Dokicloud, and Vidstream embeds.
 */
class RabbitstreamExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
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
                runCatching {
                    val obj = json.parseToJsonElement(jsonText).jsonObject
                    val tracks = obj["tracks"]?.jsonArray
                    tracks?.forEach { tElem ->
                        val track = tElem.jsonObject
                        val kind = track["kind"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (kind.contains("captions") || kind.contains("subtitles")) {
                            val label = track["label"]?.jsonPrimitive?.contentOrNull ?: "English"
                            val file = track["file"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (file.isNotBlank()) {
                                subtitleCallback(
                                    SubtitleData(
                                        language = label,
                                        url = file,
                                        isVtt = true
                                    )
                                )
                            }
                        }
                    }

                    val sources = obj["sources"]?.jsonArray
                    val firstSource = sources?.firstOrNull()?.jsonObject
                    val fileUrl = firstSource?.get("file")?.jsonPrimitive?.contentOrNull ?: ""
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

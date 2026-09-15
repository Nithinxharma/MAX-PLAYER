package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.random.Random

/**
 * Extractor for Doodstream (doodstream.com, dood.so, dood.to, dood.wf, doods.pro).
 */
class DoodExtractor : ExtractorApi() {
    companion object {
        private const val TAG = "CineHub:Extractor"
    }

    override val name: String = "DoodStream"
    override val mainUrl: String = "https://dood.to"
    override val requiresReferer: Boolean = false

    override fun canExtract(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("dood.") || lower.contains("doods.") || lower.contains("doodstream.") || lower.contains("ds2play.")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleData) -> Unit,
        callback: (ExtractorLinkData) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val embedUrl = url.replace("/d/", "/e/")
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()

            val pageBody = defaultClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string() ?: ""
            }

            // Dood pass_md5 token pattern
            val md5Match = Regex("""/pass_md5/([a-zA-Z0-9_-]+)""").find(pageBody)
            if (md5Match != null) {
                val token = md5Match.groupValues[1]
                val host = "https://" + (java.net.URI(embedUrl).host ?: "dood.to")
                val passUrl = "$host/pass_md5/$token"

                val passReq = Request.Builder()
                    .url(passUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Referer", embedUrl)
                    .build()

                val passResp = defaultClient.newCall(passReq).execute().use { it.body?.string() ?: "" }
                if (passResp.isNotBlank()) {
                    val randomStr = generateRandomString(10)
                    val streamUrl = "$passResp$randomStr?token=$token&expiry=${System.currentTimeMillis()}"

                    callback(
                        ExtractorLinkData(
                            source = name,
                            name = "$name (720p)",
                            url = streamUrl,
                            referer = embedUrl,
                            quality = "720p",
                            isM3u8 = false,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                                "Referer" to embedUrl
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DoodStream extraction failed for $url: ${e.message}")
        }
    }

    private fun generateRandomString(length: Int): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { allowedChars[Random.nextInt(allowedChars.length)] }
            .joinToString("")
    }
}

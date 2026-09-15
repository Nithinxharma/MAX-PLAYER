package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CloudStream JwPlayerHelper port for CineHub.
 * Extracts stream sources and subtitle tracks from JWPlayer javascript configurations.
 */
object JwPlayerHelper {
    private const val TAG = "CineHub:JwPlayer"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val SOURCE_REGEX = Regex("""(?i)"?sources"?\s*:\s*(\[[^\]]*\])""")
    private val TRACKS_REGEX = Regex("""(?i)"?tracks"?\s*:\s*(\[[^\]]*\])""")
    private val M3U8_REGEX = Regex("""[:=]\s*["']([^"'\s]+(?:\.m3u8|master\.txt)[^"'\s]*)["']""")

    suspend fun extractStreamLinks(
        script: String,
        sourceName: String,
        mainUrl: String,
        headers: Map<String, String> = emptyMap(),
        callback: (ExtractorLinkData) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var foundAny = false

        // 1. Try to find sources JSON array
        val sourceMatch = SOURCE_REGEX.find(script)
        if (sourceMatch != null) {
            val jsonStr = sourceMatch.groupValues[1]
                .replace(Regex("""(?<=[{,])\s*([a-zA-Z0-9_]+)\s*:"""), "\"$1\":") // quote unquoted keys
            val sources = runCatching { json.parseToJsonElement(jsonStr).jsonArray }.getOrNull()
            if (sources != null) {
                for (item in sources) {
                    val obj = item.jsonObject
                    val file = (obj["file"]?.jsonPrimitive?.contentOrNull ?: "").replace("\\/", "/")
                    val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: "Auto"
                    if (file.isNotBlank()) {
                        val fullUrl = fixUrl(file, mainUrl)
                        if (fullUrl.contains(".m3u8") || fullUrl.contains(".txt")) {
                            val generated = M3u8Helper.generateM3u8(
                                source = sourceName,
                                streamUrl = fullUrl,
                                referer = mainUrl,
                                headers = headers,
                                name = "$sourceName $label"
                            )
                            if (generated.isNotEmpty()) {
                                generated.forEach(callback)
                                foundAny = true
                            }
                        } else {
                            callback(
                                ExtractorLinkData(
                                    source = sourceName,
                                    name = "$sourceName $label",
                                    url = fullUrl,
                                    referer = mainUrl,
                                    quality = label,
                                    isM3u8 = false,
                                    headers = headers
                                )
                            )
                            foundAny = true
                        }
                    }
                }
            }
        }

        // 2. Fallback: regex search for .m3u8 links in script
        if (!foundAny) {
            M3U8_REGEX.findAll(script).forEach { match ->
                val cleanUrl = match.groupValues[1].replace("\\/", "/")
                val fullUrl = fixUrl(cleanUrl, mainUrl)
                val generated = M3u8Helper.generateM3u8(
                    source = sourceName,
                    streamUrl = fullUrl,
                    referer = mainUrl,
                    headers = headers,
                    name = sourceName
                )
                if (generated.isNotEmpty()) {
                    generated.forEach(callback)
                    foundAny = true
                }
            }
        }

        // 3. Extract subtitles / tracks
        val trackMatch = TRACKS_REGEX.find(script)
        if (trackMatch != null) {
            val jsonStr = trackMatch.groupValues[1]
                .replace(Regex("""(?<=[{,])\s*([a-zA-Z0-9_]+)\s*:"""), "\"$1\":")
            val tracks = runCatching { json.parseToJsonElement(jsonStr).jsonArray }.getOrNull()
            if (tracks != null) {
                for (item in tracks) {
                    val obj = item.jsonObject
                    val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: ""
                    val file = (obj["file"]?.jsonPrimitive?.contentOrNull ?: "").replace("\\/", "/")
                    val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: "English"
                    if (file.isNotBlank() && (kind.contains("caption") || kind.contains("subtitle") || file.endsWith(".vtt") || file.endsWith(".srt"))) {
                        subtitleCallback(
                            SubtitleData(
                                language = label,
                                url = fixUrl(file, mainUrl),
                                isVtt = file.contains(".vtt")
                            )
                        )
                    }
                }
            }
        }

        Log.d(TAG, "extractStreamLinks finished for '$sourceName', found links: $foundAny")
        foundAny
    }

    private fun fixUrl(url: String, mainUrl: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val uri = java.net.URI(mainUrl)
                "${uri.scheme}://${uri.host}$url"
            }
            else -> "$mainUrl/$url"
        }
    }
}

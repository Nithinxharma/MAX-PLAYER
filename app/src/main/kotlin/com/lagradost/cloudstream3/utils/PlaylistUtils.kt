package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import java.net.URI

object PlaylistUtils {

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeUrl).toString()
        } catch (_: Exception) {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else if (relativeUrl.startsWith("/")) {
                val protoEnd = baseUrl.indexOf("://")
                if (protoEnd != -1) {
                    val hostEnd = baseUrl.indexOf('/', protoEnd + 3)
                    val origin = if (hostEnd != -1) baseUrl.substring(0, hostEnd) else baseUrl
                    "$origin$relativeUrl"
                } else {
                    relativeUrl
                }
            } else {
                val parent = baseUrl.substringBeforeLast('/')
                "$parent/$relativeUrl"
            }
        }
    }

    suspend fun extractM3u8ToLinks(
        source: String,
        m3u8Url: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null,
        audioCallback: ((AudioFile) -> Unit)? = null
    ): List<ExtractorLink> {
        return try {
            val content = app.get(m3u8Url, headers = headers, referer = referer).text
            parseM3u8Content(source, m3u8Url, content, referer, headers, name, subtitleCallback, audioCallback)
        } catch (_: Throwable) {
            listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = m3u8Url,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
            )
        }
    }

    fun parseM3u8Content(
        source: String,
        m3u8Url: String,
        content: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null,
        audioCallback: ((AudioFile) -> Unit)? = null
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        val audioTracks = mutableListOf<AudioFile>()
        val lines = content.lines()
        var currentResolution: Int? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-MEDIA")) {
                if (line.contains("TYPE=SUBTITLES")) {
                    val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                    val nameMatch = Regex("""NAME="([^"]+)"""").find(line)
                    val langMatch = Regex("""LANGUAGE="([^"]+)"""").find(line)
                    val subUri = uriMatch?.groupValues?.getOrNull(1)
                    val subName = nameMatch?.groupValues?.getOrNull(1) ?: langMatch?.groupValues?.getOrNull(1) ?: "Subtitle"
                    if (!subUri.isNullOrBlank()) {
                        val fullSubUrl = resolveUrl(m3u8Url, subUri)
                        subtitleCallback?.invoke(SubtitleFile(lang = subName, url = fullSubUrl, headers = headers))
                    }
                } else if (line.contains("TYPE=AUDIO")) {
                    val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                    val nameMatch = Regex("""NAME="([^"]+)"""").find(line)
                    val langMatch = Regex("""LANGUAGE="([^"]+)"""").find(line)
                    val audioUri = uriMatch?.groupValues?.getOrNull(1)
                    val audioName = nameMatch?.groupValues?.getOrNull(1) ?: langMatch?.groupValues?.getOrNull(1) ?: "Audio"
                    if (!audioUri.isNullOrBlank()) {
                        val fullAudioUrl = resolveUrl(m3u8Url, audioUri)
                        val audioFile = AudioFile(
                            url = fullAudioUrl,
                            lang = audioName,
                            isM3u8 = fullAudioUrl.contains(".m3u8"),
                            headers = headers
                        )
                        audioTracks.add(audioFile)
                        audioCallback?.invoke(audioFile)
                    }
                }
            } else if (line.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("""RESOLUTION=\d+x(\d+)""").find(line)
                currentResolution = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val streamUrl = resolveUrl(m3u8Url, line)
                val baseName = name ?: source
                val displayName = if (currentResolution != null && !baseName.contains("${currentResolution}p", ignoreCase = true)) {
                    "$baseName ${currentResolution}p"
                } else {
                    baseName
                }
                links.add(
                    ExtractorLink(
                        source = source,
                        name = displayName,
                        url = streamUrl,
                        referer = referer,
                        quality = currentResolution ?: Qualities.Unknown.value,
                        type = ExtractorLinkType.M3U8,
                        headers = headers,
                        audioTracks = audioTracks.toList()
                    )
                )
                currentResolution = null
            }
        }

        if (links.isEmpty()) {
            links.add(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = m3u8Url,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = headers,
                    audioTracks = audioTracks.toList()
                )
            )
        }

        return links
    }

    suspend fun extractDashToLinks(
        source: String,
        dashUrl: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null
    ): List<ExtractorLink> {
        return DashHelper.generateDash(
            source = source,
            mpdUrl = dashUrl,
            referer = referer,
            headers = headers,
            name = name,
            subtitleCallback = subtitleCallback
        )
    }
}

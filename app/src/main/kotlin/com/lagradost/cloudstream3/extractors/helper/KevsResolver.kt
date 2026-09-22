package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

object KevsResolver {

    private val VIDEO_URL_REGEX = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8|webm|mpd|m4v)[^\s"'<>]*""")
    private val FILE_PROPERTY_REGEX = Regex("""["']?(?:file|src|source|stream_url)["']?\s*:\s*["']([^"']+)["']""")
    private val TOKEN_REGEX = Regex("""(?i)(?:var\s+|let\s+|const\s+)?([a-zA-Z0-9_$]*(?:token|auth|key|hash|sig|signature|pass|verify)[a-zA-Z0-9_$]*)\s*[:=]\s*["']([^"']+)["']""")

    /**
     * Follows HTTP 3xx redirects to discover the destination endpoint while retaining original headers.
     */
    suspend fun resolveRedirect(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String = ""
    ): String {
        return try {
            val response = app.get(
                url = url,
                headers = headers,
                referer = referer,
                allowRedirects = true
            )
            response.url
        } catch (_: Throwable) {
            // Manual fallback if redirect loop or custom headers needed
            try {
                var currentUrl = url
                var hops = 0
                val client = OkHttpClient.Builder().followRedirects(false).build()
                while (hops++ < 10) {
                    val requestBuilder = Request.Builder().url(currentUrl)
                    headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                    if (referer.isNotBlank()) requestBuilder.header("Referer", referer)

                    val resp = client.newCall(requestBuilder.build()).execute()
                    val code = resp.code
                    val location = resp.header("Location")
                    resp.close()

                    if (code in 300..399 && !location.isNullOrBlank()) {
                        currentUrl = URI(currentUrl).resolve(location).toString()
                    } else {
                        break
                    }
                }
                currentUrl
            } catch (_: Throwable) {
                url
            }
        }
    }

    /**
     * Unpacks p.a.c.k.e.r or obfuscated JavaScript from HTML or script contents.
     */
    fun unpackAndExtract(htmlOrScript: String): String {
        val packed = getPacked(htmlOrScript)
        return if (!packed.isNullOrBlank()) {
            getAndUnpack(packed)
        } else if (htmlOrScript.contains("eval(function(p,a,c,k,e,")) {
            getAndUnpack(htmlOrScript)
        } else {
            htmlOrScript
        }
    }

    /**
     * Extracts token or session keys from script contents.
     */
    fun extractTokens(script: String): Map<String, String> {
        val tokens = mutableMapOf<String, String>()
        TOKEN_REGEX.findAll(script).forEach { match ->
            val key = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val value = match.groupValues.getOrNull(2).orEmpty()
            if (key.isNotBlank() && value.isNotBlank()) {
                tokens[key] = value
            }
        }
        return tokens
    }

    /**
     * Resolves stream links from a host URL commonly used across French and Spanish mirrors.
     */
    suspend fun resolve(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        name: String = "Kevs",
        subtitleCallback: ((SubtitleFile) -> Unit)? = null
    ): List<ExtractorLink> {
        val finalUrl = resolveRedirect(url, headers, referer.orEmpty())
        val response = try {
            app.get(finalUrl, headers = headers, referer = referer.orEmpty()).text
        } catch (_: Throwable) {
            return emptyList()
        }

        val unpacked = unpackAndExtract(response)
        val links = mutableListOf<ExtractorLink>()

        // 1. Search for JSON/JS file properties
        FILE_PROPERTY_REGEX.findAll(unpacked).forEach { match ->
            val streamUrl = match.groupValues.getOrNull(1)?.replace("\\/", "/") ?: return@forEach
            if (streamUrl.contains(".m3u8")) {
                links.addAll(
                    M3u8Helper.generateM3u8(
                        source = name,
                        streamUrl = streamUrl,
                        referer = finalUrl,
                        headers = headers,
                        name = name,
                        subtitleCallback = subtitleCallback
                    )
                )
            } else if (streamUrl.contains(".mp4") || streamUrl.contains(".webm")) {
                links.add(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = streamUrl,
                        referer = finalUrl,
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.VIDEO,
                        headers = headers
                    )
                )
            }
        }

        // 2. Direct regex matching if no property found
        if (links.isEmpty()) {
            VIDEO_URL_REGEX.findAll(unpacked).forEach { match ->
                val streamUrl = match.value.replace("\\/", "/")
                if (streamUrl.contains(".m3u8")) {
                    links.addAll(
                        M3u8Helper.generateM3u8(
                            source = name,
                            streamUrl = streamUrl,
                            referer = finalUrl,
                            headers = headers,
                            name = name,
                            subtitleCallback = subtitleCallback
                        )
                    )
                } else if (streamUrl.contains(".mp4") || streamUrl.contains(".webm")) {
                    links.add(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = streamUrl,
                            referer = finalUrl,
                            quality = Qualities.Unknown.value,
                            type = ExtractorLinkType.VIDEO,
                            headers = headers
                        )
                    )
                }
            }
        }

        return links.distinctBy { it.url }
    }

    suspend fun getStreamLinks(
        url: String,
        referer: String? = null
    ): List<ExtractorLink> = resolve(url, referer)
}

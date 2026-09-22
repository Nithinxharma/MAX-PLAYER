package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app

object DashHelper {

    suspend fun generateDash(
        source: String,
        mpdUrl: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null
    ): List<ExtractorLink> {
        val headersWithReferer = buildMap {
            putAll(headers)
            if (referer.isNotBlank() && !containsKey("Referer") && !containsKey("referer")) {
                put("Referer", referer)
            }
        }

        return try {
            val response = app.get(mpdUrl, headers = headersWithReferer, referer = referer).text
            parseMpdToLinks(
                source = source,
                mpdUrl = mpdUrl,
                xmlContent = response,
                referer = referer,
                headers = headersWithReferer,
                name = name,
                subtitleCallback = subtitleCallback
            )
        } catch (_: Throwable) {
            listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = mpdUrl,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.DASH,
                    headers = headersWithReferer
                )
            )
        }
    }

    fun parseMpdToLinks(
        source: String,
        mpdUrl: String,
        xmlContent: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null
    ): List<ExtractorLink> {
        val manifest = try {
            MpdManifestParser.parse(xmlContent, mpdUrl)
        } catch (_: Throwable) {
            null
        }

        if (manifest == null) {
            return listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = mpdUrl,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.DASH,
                    headers = headers
                )
            )
        }

        val audioTracks = mutableListOf<AudioFile>()
        val subtitleFiles = mutableListOf<SubtitleFile>()
        val videoRepresentations = mutableListOf<Pair<MpdAdaptationSet, MpdRepresentation>>()

        for (period in manifest.periods) {
            for (adapt in period.adaptationSets) {
                val isAudio = adapt.contentType?.equals("audio", ignoreCase = true) == true ||
                        adapt.mimeType?.contains("audio", ignoreCase = true) == true
                val isSubtitle = adapt.contentType?.equals("text", ignoreCase = true) == true ||
                        adapt.mimeType?.contains("vtt", ignoreCase = true) == true ||
                        adapt.mimeType?.contains("ttml", ignoreCase = true) == true ||
                        adapt.mimeType?.contains("sub", ignoreCase = true) == true
                val isVideo = adapt.contentType?.equals("video", ignoreCase = true) == true ||
                        adapt.mimeType?.contains("video", ignoreCase = true) == true ||
                        (!isAudio && !isSubtitle)

                if (isAudio) {
                    val lang = adapt.lang ?: "Audio"
                    for (rep in adapt.representations) {
                        val trackUrl = rep.baseUrl ?: adapt.baseUrl ?: mpdUrl
                        audioTracks.add(
                            AudioFile(
                                url = trackUrl,
                                lang = lang,
                                isM3u8 = false,
                                headers = headers
                            )
                        )
                    }
                } else if (isSubtitle) {
                    val lang = adapt.lang ?: "Subtitle"
                    for (rep in adapt.representations) {
                        val subUrl = rep.baseUrl ?: adapt.baseUrl ?: continue
                        val sub = SubtitleFile(
                            lang = lang,
                            url = subUrl,
                            headers = headers
                        )
                        subtitleFiles.add(sub)
                        subtitleCallback?.invoke(sub)
                    }
                } else if (isVideo) {
                    for (rep in adapt.representations) {
                        videoRepresentations.add(adapt to rep)
                    }
                }
            }
        }

        val links = mutableListOf<ExtractorLink>()
        val baseName = name ?: source

        if (videoRepresentations.isNotEmpty()) {
            val sortedVideos = videoRepresentations.sortedByDescending { it.second.height ?: it.second.bandwidth.toInt() }
            for ((_, rep) in sortedVideos) {
                val height = rep.height
                val quality = when {
                    height != null && height >= 2160 -> Qualities.P2160.value
                    height != null && height >= 1440 -> Qualities.P1440.value
                    height != null && height >= 1080 -> Qualities.P1080.value
                    height != null && height >= 720 -> Qualities.P720.value
                    height != null && height >= 480 -> Qualities.P480.value
                    height != null && height >= 360 -> Qualities.P360.value
                    height != null && height >= 240 -> Qualities.P240.value
                    height != null -> height
                    else -> Qualities.Unknown.value
                }

                val repDisplayName = if (height != null && !baseName.contains("${height}p", ignoreCase = true)) {
                    "$baseName ${height}p"
                } else {
                    baseName
                }

                val repUrl = if (!rep.baseUrl.isNullOrBlank() && (rep.baseUrl.endsWith(".mp4") || rep.baseUrl.endsWith(".m4v"))) {
                    rep.baseUrl
                } else {
                    mpdUrl
                }

                links.add(
                    ExtractorLink(
                        source = source,
                        name = repDisplayName,
                        url = repUrl,
                        referer = referer,
                        quality = quality,
                        type = if (repUrl.endsWith(".mpd") || repUrl == mpdUrl) ExtractorLinkType.DASH else ExtractorLinkType.VIDEO,
                        headers = headers,
                        audioTracks = audioTracks
                    )
                )
            }
        }

        if (links.isEmpty()) {
            links.add(
                ExtractorLink(
                    source = source,
                    name = baseName,
                    url = mpdUrl,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.DASH,
                    headers = headers,
                    audioTracks = audioTracks
                )
            )
        }

        return links
    }
}

package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile

object MpdHelper {

    suspend fun generateDash(
        source: String,
        mpdUrl: String,
        referer: String = "",
        headers: Map<String, String> = emptyMap(),
        name: String? = null,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null
    ): List<ExtractorLink> {
        return DashHelper.generateDash(
            source = source,
            mpdUrl = mpdUrl,
            referer = referer,
            headers = headers,
            name = name,
            subtitleCallback = subtitleCallback
        )
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
        return DashHelper.parseMpdToLinks(
            source = source,
            mpdUrl = mpdUrl,
            xmlContent = xmlContent,
            referer = referer,
            headers = headers,
            name = name,
            subtitleCallback = subtitleCallback
        )
    }

    fun parse(xmlContent: String, sourceUrl: String): MpdManifest {
        return MpdManifestParser.parse(xmlContent, sourceUrl)
    }
}

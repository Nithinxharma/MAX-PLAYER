package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile

object ExtractorManager {
    fun getExtractorForUrl(url: String): ExtractorApi? {
        return com.lagradost.cloudstream3.utils.getExtractorForUrl(url)
    }

    fun getExtractorApiFromName(name: String): ExtractorApi? {
        return com.lagradost.cloudstream3.utils.getExtractorApiFromName(name)
    }

    suspend fun loadExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return com.lagradost.cloudstream3.utils.loadExtractor(
            url = url,
            referer = referer,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

    suspend fun loadExtractor(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return com.lagradost.cloudstream3.utils.loadExtractor(
            url = url,
            referer = null,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

    suspend fun loadExtractor(
        url: String,
        referer: String?,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return com.lagradost.cloudstream3.utils.loadExtractor(
            url = url,
            referer = referer,
            subtitleCallback = {},
            callback = callback
        )
    }

    suspend fun loadExtractor(
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return com.lagradost.cloudstream3.utils.loadExtractor(
            url = url,
            referer = null,
            subtitleCallback = {},
            callback = callback
        )
    }
}

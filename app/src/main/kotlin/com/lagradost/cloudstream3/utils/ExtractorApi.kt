package com.lagradost.cloudstream3.utils

abstract class ExtractorApi {
    open val name: String = ""
    open val mainUrl: String = ""
    open val requiresReferer: Boolean = false
    open var sourcePlugin: String? = null

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ) {}
}

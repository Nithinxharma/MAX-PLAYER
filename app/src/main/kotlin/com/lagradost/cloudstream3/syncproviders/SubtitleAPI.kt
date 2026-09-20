package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource

abstract class SubtitleAPI : AuthAPI() {
    override val requiresLogin: Boolean = false

    abstract val mainUrl: String

    abstract suspend fun search(query: SubtitleSearch): List<SubtitleEntity>?

    abstract suspend fun load(data: SubtitleEntity): SubtitleResource?
}

package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.SubtitleHelper

class SubSourceApi : SubtitleAPI() {
    override val name = "SubSource"
    override val idPrefix = "subsource"
    override val mainUrl = "https://api.subsource.net/api"

    override suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? {
        val queryUrl = "$mainUrl/searchMovie?query=${java.net.URLEncoder.encode(query.query, "UTF-8")}"
        val res = app.get(queryUrl).text
        val parsed = tryParseJson<SubSourceSearchResponse>(res) ?: return null
        val movie = parsed.found?.firstOrNull() ?: return null
        val subRes = app.post(
            "$mainUrl/getSub",
            json = mapOf("movie" to (movie.linkName ?: ""), "langs" to listOf("English"))
        ).text
        val subParsed = tryParseJson<SubSourceSubResponse>(subRes) ?: return null
        return subParsed.subs?.mapNotNull { item ->
            val fullLink = item.fullLink ?: return@mapNotNull null
            SubtitleEntity(
                idPrefix = idPrefix,
                name = item.releaseName ?: movie.title ?: query.query,
                lang = item.lang ?: "en",
                data = fullLink,
                source = name,
                epNumber = query.epNumber,
                seasonNumber = query.seasonNumber,
                isHearingImpaired = item.hi == 1
            )
        }
    }

    override suspend fun load(data: SubtitleEntity): SubtitleResource? {
        val tokenRes = app.post(
            "$mainUrl/downloadSub",
            json = mapOf("subPath" to data.data)
        ).text
        val parsed = tryParseJson<SubSourceDownloadResponse>(tokenRes) ?: return null
        val downloadUrl = parsed.downloadUrl ?: return null
        val resource = SubtitleResource()
        resource.addUrl(downloadUrl, data.name)
        return resource
    }

    data class SubSourceSearchResponse(
        val success: Boolean?,
        val found: List<MovieItem>?
    ) {
        data class MovieItem(val title: String?, val linkName: String?, val year: Int?)
    }

    data class SubSourceSubResponse(
        val success: Boolean?,
        val subs: List<SubItem>?
    ) {
        data class SubItem(val releaseName: String?, val lang: String?, val fullLink: String?, val hi: Int?)
    }

    data class SubSourceDownloadResponse(
        val success: Boolean?,
        val downloadUrl: String?
    )
}

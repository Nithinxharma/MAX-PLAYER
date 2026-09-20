package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.SubtitleHelper

class SubDlApi : SubtitleAPI() {
    override val name = "SubDL"
    override val idPrefix = "subdl"
    override val mainUrl = "https://api.subdl.com/api/v1"
    private val apiKey = "4a-Z9M1ZqR1x3K3n5p2v"

    override suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? {
        val queryUrl = buildString {
            append("$mainUrl/subtitles?")
            append("api_key=$apiKey")
            query.imdbId?.let { append("&imdb_id=$it") } ?: append("&film_name=${java.net.URLEncoder.encode(query.query, "UTF-8")}")
            query.seasonNumber?.let { append("&season_number=$it") }
            query.epNumber?.let { append("&episode_number=$it") }
            append("&type=${if (query.seasonNumber != null) "tv" else "movie"}")
        }
        val res = app.get(queryUrl).text
        val parsed = tryParseJson<SubDlSearchResponse>(res) ?: return null
        return parsed.subtitles?.mapNotNull { item ->
            val url = item.url ?: return@mapNotNull null
            SubtitleEntity(
                idPrefix = idPrefix,
                name = item.release_name ?: item.name ?: query.query,
                lang = item.lang ?: "en",
                data = if (url.startsWith("http")) url else "https://dl.subdl.com$url",
                source = name,
                epNumber = item.episode,
                seasonNumber = item.season,
                isHearingImpaired = item.hi == true
            )
        }
    }

    override suspend fun load(data: SubtitleEntity): SubtitleResource? {
        val url = data.data
        if (url.isBlank()) return null
        val resource = SubtitleResource()
        resource.addUrl(url, data.name)
        return resource
    }

    data class SubDlSearchResponse(
        val status: Boolean?,
        val subtitles: List<SubDlSubtitle>?
    ) {
        data class SubDlSubtitle(
            val release_name: String?,
            val name: String?,
            val lang: String?,
            val url: String?,
            val season: Int?,
            val episode: Int?,
            val hi: Boolean?
        )
    }
}

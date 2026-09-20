package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.SubtitleHelper

class OpenSubtitlesApi : SubtitleAPI() {
    override val name = "OpenSubtitles"
    override val idPrefix = "opensubtitles"
    override val mainUrl = "https://api.opensubtitles.com/api/v1"
    private val apiKey = "eLgB9b1Yn9KqWjH2sM3dZ4fX5c6v7b8a"

    override suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? {
        val queryUrl = buildString {
            append("$mainUrl/subtitles?")
            append("query=${java.net.URLEncoder.encode(query.query, "UTF-8")}")
            query.imdbId?.let { append("&imdb_id=${it.removePrefix("tt")}") }
            query.tmdbId?.let { append("&tmdb_id=$it") }
            query.seasonNumber?.let { append("&season_number=$it") }
            query.epNumber?.let { append("&episode_number=$it") }
        }
        val res = app.get(
            queryUrl,
            headers = mapOf(
                "Api-Key" to apiKey,
                "User-Agent" to "CloudStream v3"
            )
        ).text
        val parsed = tryParseJson<OpenSubtitlesSearchResponse>(res) ?: return null
        return parsed.data?.mapNotNull { item ->
            val attr = item.attributes ?: return@mapNotNull null
            val file = attr.files?.firstOrNull() ?: return@mapNotNull null
            SubtitleEntity(
                idPrefix = idPrefix,
                name = attr.release ?: attr.feature_details?.movie_name ?: query.query,
                lang = attr.language ?: "en",
                data = file.file_id?.toString() ?: "",
                source = name,
                epNumber = attr.feature_details?.episode_number,
                seasonNumber = attr.feature_details?.season_number,
                isHearingImpaired = attr.hearing_impaired == true
            )
        }
    }

    override suspend fun load(data: SubtitleEntity): SubtitleResource? {
        val fileId = data.data
        if (fileId.isBlank()) return null
        val res = app.post(
            "$mainUrl/download",
            headers = mapOf(
                "Api-Key" to apiKey,
                "User-Agent" to "CloudStream v3",
                "Content-Type" to "application/json"
            ),
            json = mapOf("file_id" to fileId.toIntOrNull())
        ).text
        val parsed = tryParseJson<OpenSubtitlesDownloadResponse>(res) ?: return null
        val link = parsed.link ?: return null
        val resource = SubtitleResource()
        resource.addUrl(link, data.name)
        return resource
    }

    data class OpenSubtitlesSearchResponse(
        val data: List<OpenSubItem>?
    ) {
        data class OpenSubItem(
            val id: String?,
            val attributes: OpenSubAttributes?
        )
        data class OpenSubAttributes(
            val language: String?,
            val release: String?,
            val hearing_impaired: Boolean?,
            val files: List<OpenSubFile>?,
            val feature_details: FeatureDetails?
        )
        data class OpenSubFile(val file_id: Long?, val file_name: String?)
        data class FeatureDetails(val movie_name: String?, val season_number: Int?, val episode_number: Int?)
    }

    data class OpenSubtitlesDownloadResponse(
        val link: String?,
        val file_name: String?
    )
}

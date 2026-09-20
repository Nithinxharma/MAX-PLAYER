package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class SimklApi : SyncAPI() {
    override val name = "Simkl"
    override val idPrefix = "simkl"
    override val requiresLogin = true
    override val mainUrl = "https://simkl.com"
    override val syncIdName = SyncIdName.Simkl
    override val createAccountUrl = "https://simkl.com/signup/"

    private val clientId = "0417dd03714b870ad7e06a87756f70ba0ae3c0e396996d482c3c97ea07d4b470"

    override fun loginInfo(): AuthLoginPage {
        return AuthLoginPage(
            url = "https://simkl.com/oauth/authorize?response_type=code&client_id=$clientId&redirect_uri=cloudstreamapp://simkl",
            requiresPKCE = false
        )
    }

    override suspend fun search(query: String): List<SyncSearchResult>? {
        val res = app.get(
            "https://api.simkl.com/search/text?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=20&client_id=$clientId"
        ).text
        val parsed = tryParseJson<List<SimklSearchItem>>(res) ?: return null
        return parsed.map { item ->
            val ids = item.ids
            val simklId = ids?.simkl?.toString() ?: ""
            SyncSearchResult(
                name = item.title ?: "",
                apiName = name,
                syncId = simklId,
                url = "$mainUrl/${item.type}/$simklId",
                posterUrl = item.poster?.let { "https://simkl.in/posters/${it}_m.webp" },
                type = when (item.type) {
                    "anime" -> TvType.Anime
                    "show" -> TvType.TvSeries
                    else -> TvType.Movie
                },
                score = item.ratings?.simkl?.rating?.let { Score.from10(it) }
            )
        }
    }

    data class SimklSearchItem(
        val title: String?,
        val year: Int?,
        val type: String?,
        val poster: String?,
        val ids: SimklIds?,
        val ratings: SimklRatings?
    ) {
        data class SimklIds(val simkl: Long?, val imdb: String?, val tmdb: String?, val mal: String?)
        data class SimklRatings(val simkl: SimklRating?)
        data class SimklRating(val rating: Double?, val votes: Int?)
    }
}

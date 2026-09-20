package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object Kitsu {
    suspend fun getKitsuEpisodesDetails(kitsuId: String): List<Episode>? {
        return null
    }
}

class KitsuApi : SyncAPI() {
    override val name = "Kitsu"
    override val idPrefix = "kitsu"
    override val requiresLogin = true
    override val mainUrl = "https://kitsu.io"
    override val syncIdName = SyncIdName.Kitsu
    override val createAccountUrl = "https://kitsu.io/explore/anime"

    override suspend fun search(query: String): List<SyncSearchResult>? {
        val res = app.get(
            "https://kitsu.io/api/edge/anime?filter[text]=${java.net.URLEncoder.encode(query, "UTF-8")}&page[limit]=20"
        ).text
        val parsed = tryParseJson<KitsuSearchResponse>(res) ?: return null
        return parsed.data?.mapNotNull { item ->
            val attr = item.attributes ?: return@mapNotNull null
            SyncSearchResult(
                name = attr.canonicalTitle ?: attr.titles?.en ?: attr.titles?.en_jp ?: "",
                apiName = name,
                syncId = item.id ?: return@mapNotNull null,
                url = "$mainUrl/anime/${item.id}",
                posterUrl = attr.posterImage?.original ?: attr.posterImage?.large,
                type = TvType.Anime,
                score = attr.averageRating?.let { Score.from100(it.toDoubleOrNull()?.toInt()) }
            )
        }
    }

    data class KitsuSearchResponse(
        val data: List<KitsuData>?
    ) {
        data class KitsuData(
            val id: String?,
            val attributes: KitsuAttributes?
        )
        data class KitsuAttributes(
            val canonicalTitle: String?,
            val titles: KitsuTitles?,
            val averageRating: String?,
            val posterImage: KitsuImage?,
            val episodeCount: Int?
        )
        data class KitsuTitles(val en: String?, val en_jp: String?, val ja_jp: String?)
        data class KitsuImage(val tiny: String?, val small: String?, val medium: String?, val large: String?, val original: String?)
    }
}

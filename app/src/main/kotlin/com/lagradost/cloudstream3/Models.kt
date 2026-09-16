package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink

enum class TvType {
    Movie,
    TvSeries,
    Anime,
    AnimeMovie,
    OVA,
    Cartoon,
    Documentary,
    AsianDrama,
    Live,
    NSFW,
    Others
}

enum class DubStatus {
    None,
    Dubbed,
    Subbed
}

enum class SearchQuality {
    FourK,
    HD,
    SD,
    CAM,
    TeleSync
}

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType?
    var posterUrl: String?
    var id: Int?
    var quality: SearchQuality?
    var posterHeaders: Map<String, String>?
}

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    val year: Int? = null
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    val episodes: Int? = null
) : SearchResponse

data class LiveSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Live,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null
) : SearchResponse

data class Actor(
    val name: String,
    val image: String? = null,
    val role: String? = null
)

interface LoadResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?
    var rating: Int?
    var tags: List<String>?
    var duration: Int?
    var actors: List<Actor>?
    var recommendations: List<SearchResponse>?
    var posterHeaders: Map<String, String>?
}

data class MovieLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.Movie,
    val dataUrl: String,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var actors: List<Actor>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var posterHeaders: Map<String, String>? = null
) : LoadResponse

data class Episode(
    val data: String,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val rating: Int? = null,
    val posterUrl: String? = null,
    val description: String? = null,
    val date: String? = null
)

data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.TvSeries,
    val episodes: List<Episode>,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var actors: List<Actor>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var posterHeaders: Map<String, String>? = null
) : LoadResponse

data class SubtitleFile(
    val lang: String,
    val url: String
)

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>
)

data class HomePageResponse(
    val items: List<HomePageList>
)

abstract class MainAPI {
    open var name: String = "Unknown Provider"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open var hasMainPage: Boolean = false
    open var vpnStatus: Int = 0

    open suspend fun search(query: String): List<SearchResponse> = emptyList()
    open suspend fun loadMainPage(page: Int, name: String? = null): HomePageResponse? = null
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ): Boolean = false
}

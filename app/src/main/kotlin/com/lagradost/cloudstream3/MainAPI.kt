package com.lagradost.cloudstream3


import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi

enum class TvType {
    Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, Documentary, AsianDrama, Live, NSFW, Others
}

enum class DubStatus {
    None, Dubbed, Subbed
}

enum class SearchQuality(val value: Int) {
    Cam(1), CamRip(2), HdCam(3), Telesync(4), WorkPrint(5), Telecine(6), HQ(7), HD(8), HDR(9), BlueRay(10), DVD(11), SD(12), FourK(13), UHD(14), SDR(15), WebRip(16)
}

enum class ShowStatus {
    Completed, Ongoing, Canceled
}

class Score(var score: Double, var max: Int = 100) {
    companion object {
        fun fromOld(rating: Int?): Score? = rating?.let { Score(it.toDouble(), 100) }
        fun from(rating: Int?, max: Int = 100): Score? = rating?.let { Score(it.toDouble(), max) }
    }
}

data class TrailerData(
    val url: String,
    val raw: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val extractorUrl: String? = null
)

data class ActorData(
    var actor: Actor,
    var roleString: String? = null,
    var mainActor: Boolean = false
)

data class Actor(
    val name: String,
    val image: String? = null,
    val role: String? = null
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean
)

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
    var score: Score?
}

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var episodes: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null
) : SearchResponse

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: MutableSet<DubStatus>? = null,
    var otherName: String? = null,
    var episodes: MutableMap<DubStatus, Int> = mutableMapOf(),
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null
) : SearchResponse

data class TorrentSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null
) : SearchResponse

// Extensions to create SearchResponses just like upstream does

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?
    var score: Score?
    var tags: List<String>?
    var duration: Int?
    var trailers: MutableList<TrailerData>
    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var syncData: MutableMap<String, String>
    var posterHeaders: Map<String, String>?
    var backgroundPosterUrl: String?
    var logoUrl: String?
    var contentRating: String?
    var uniqueUrl: String
}

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    var dataUrl: String,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = ""
) : LoadResponse

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    var episodes: List<Episode>,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    var showStatus: ShowStatus? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = ""
) : LoadResponse

data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var score: Score? = null,
    var description: String? = null,
    var date: Long? = null,
    var runTime: Int? = null
)

data class SubtitleFile(
    var lang: String,
    var url: String,
    var headers: Map<String, String>? = null
)

data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

data class SearchResponseList(
    val items: List<SearchResponse>,
    val hasNext: Boolean = false
)

const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

data class SettingsJson(val enableAdult: Boolean = false)

abstract class MainAPI {
    companion object {
        val settingsForProvider: SettingsJson = SettingsJson()
    }
    open var name: String = "Unknown Provider"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open var sourcePlugin: String? = null
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open var hasMainPage: Boolean = false

    open suspend fun search(query: String): List<SearchResponse> = emptyList()
    open suspend fun search(query: String, page: Int): SearchResponseList? = null
    
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun loadMainPage(page: Int, name: String? = null): HomePageResponse? = null
    
    open suspend fun load(url: String): LoadResponse? = null
    
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean = false
}

fun MainAPI.newMovieSearchResponse(name: String, url: String, type: TvType = TvType.Movie, fix: Boolean = true, initializer: MovieSearchResponse.() -> Unit = {}): MovieSearchResponse {
    return MovieSearchResponse(name, url, this.name, type).apply(initializer)
}
fun MainAPI.newTvSeriesSearchResponse(name: String, url: String, type: TvType = TvType.TvSeries, fix: Boolean = true, initializer: TvSeriesSearchResponse.() -> Unit = {}): TvSeriesSearchResponse {
    return TvSeriesSearchResponse(name, url, this.name, type).apply(initializer)
}
fun MainAPI.newAnimeSearchResponse(name: String, url: String, type: TvType = TvType.Anime, fix: Boolean = true, initializer: AnimeSearchResponse.() -> Unit = {}): AnimeSearchResponse {
    return AnimeSearchResponse(name, url, this.name, type).apply(initializer)
}
fun MainAPI.newTorrentSearchResponse(name: String, url: String, type: TvType = TvType.Others, fix: Boolean = true, initializer: TorrentSearchResponse.() -> Unit = {}): TorrentSearchResponse {
    return TorrentSearchResponse(name, url, this.name, type).apply(initializer)
}
fun MainAPI.newMovieLoadResponse(name: String, url: String, type: TvType, dataUrl: String, initializer: MovieLoadResponse.() -> Unit = {}): MovieLoadResponse {
    return MovieLoadResponse(name, url, this.name, type, dataUrl).apply(initializer)
}
fun MainAPI.newTvSeriesLoadResponse(name: String, url: String, type: TvType, episodes: List<Episode>, initializer: TvSeriesLoadResponse.() -> Unit = {}): TvSeriesLoadResponse {
    return TvSeriesLoadResponse(name, url, this.name, type, episodes).apply(initializer)
}
fun newEpisode(data: String, initializer: Episode.() -> Unit = {}): Episode {
    return Episode(data).apply(initializer)
}
fun newHomePageResponse(items: List<HomePageList>, hasNext: Boolean = false): HomePageResponse {
    return HomePageResponse(items, hasNext)
}
fun newSearchResponseList(items: List<SearchResponse>, hasNext: Boolean = false): SearchResponseList {
    return SearchResponseList(items, hasNext)
}

fun base64DecodeArray(string: String): ByteArray {
    return java.util.Base64.getDecoder().decode(string)
}
fun base64Decode(string: String): String {
    val bytes = base64DecodeArray(string)
    return buildString(bytes.size) {
        for (b in bytes) {
            append((b.toInt() and 0xFF).toChar())
        }
    }
}
fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}
fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http") || url.startsWith("{\"") || url.startsWith("[")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }
    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}
fun LoadResponse.addImdbId(id: String?) {
    if (id != null) this.syncData["Imdb"] = id
}
fun LoadResponse.addTMDbId(id: String?) {
    if (id != null) this.syncData["Tmdb"] = id
}

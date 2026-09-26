package com.lagradost.cloudstream3

import androidx.annotation.Keep
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.serialization.json.Json

val json: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

val mapper: JsonMapper = JsonMapper.builder()
    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

fun Any.toJson(): String = mapper.writeValueAsString(this)
inline fun <reified T : Any> parseJson(value: String): T = mapper.readValue(value)
inline fun <reified T> tryParseJson(value: String?): T? {
    if (value.isNullOrBlank()) return null
    return try {
        mapper.readValue(value)
    } catch (_: Throwable) {
        null
    }
}

suspend fun newSubtitleFile(
    lang: String,
    url: String,
    initializer: suspend SubtitleFile.() -> Unit = {}
): SubtitleFile {
    val sub = SubtitleFile(lang, url)
    sub.initializer()
    return sub
}

fun newSubtitleFile(
    lang: String,
    url: String
): SubtitleFile = SubtitleFile(lang, url)

@Keep
enum class TvType {
    Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, Documentary, AsianDrama, Live, NSFW, Others
}

@Keep
enum class DubStatus {
    None, Dubbed, Subbed
}

@Keep
enum class SearchQuality(val value: Int) {
    Cam(1), CamRip(2), HdCam(3), Telesync(4), WorkPrint(5), Telecine(6), HQ(7), HD(8), HDR(9), BlueRay(10), DVD(11), SD(12), FourK(13), UHD(14), SDR(15), WebRip(16)
}

enum class ShowStatus {
    Completed, Ongoing, Canceled
}

class Score(var score: Double, var max: Int = 100) {
    fun toInt(targetMax: Int = 100): Int = if (max > 0) ((score / max) * targetMax).toInt() else score.toInt()

    companion object {
        fun fromOld(rating: Int?): Score? = rating?.let { Score(it.toDouble(), 100) }
        fun from(rating: Int?, max: Int = 100): Score? = rating?.let { Score(it.toDouble(), max) }
        fun from100(rating: Int?): Score? = rating?.let { Score(it.toDouble(), 100) }
        fun from10(rating: Double?): Score? = rating?.let { Score(it, 10) }
        fun from10(rating: Float?): Score? = rating?.toDouble()?.let { Score(it, 10) }
        fun from10(rating: String?): Score? = rating?.toDoubleOrNull()?.let { Score(it, 10) }
    }
}

data class NextAiring(
    val episode: Int,
    val unixTime: Long
)

data class TrailerData(
    val url: String,
    val raw: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val extractorUrl: String? = null
)

enum class ActorRole {
    Main,
    Supporting,
    Background
}

class ErrorLoadingException(message: String) : Exception(message)

@Keep
data class ActorData @JvmOverloads constructor(
    var actor: Actor,
    var role: ActorRole? = null,
    var roleString: String? = null,
    var voiceActor: Actor? = null
) {
    constructor(actor: Actor, roleString: String?) : this(actor, null, roleString, null)
}

@Keep
data class Actor(
    val name: String,
    val image: String? = null
) {
    var role: ActorRole? = null
    var roleString: String? = null
    var voiceActor: Actor? = null

    constructor(name: String, image: String?, role: String?) : this(name, image) {
        this.roleString = role
    }

    constructor(name: String, image: String?, role: ActorRole?) : this(name, image) {
        this.role = role
    }

    constructor(name: String, image: String?, role: ActorRole?, roleString: String?, voiceActor: Actor?) : this(name, image) {
        this.role = role
        this.roleString = roleString
        this.voiceActor = voiceActor
    }
}

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> {
    return elements.map { (data, name) -> MainPageData(name = name, data = data) }
}

fun mainPageOf(vararg elements: MainPageData): List<MainPageData> {
    return elements.toList()
}

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
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

data class LiveSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.Live,
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

    companion object {
        fun LoadResponse.addTrailer(url: String?) {
            if (url.isNullOrBlank()) return
            this.trailers.add(TrailerData(url))
        }

        fun LoadResponse.addTrailer(trailer: TrailerData?) {
            if (trailer != null) this.trailers.add(trailer)
        }

        fun LoadResponse.addAniListId(id: Int?) {
            if (id != null) this.syncData["Anilist"] = id.toString()
        }

        fun LoadResponse.addAniListId(id: String?) {
            if (id != null) this.syncData["Anilist"] = id
        }

        fun LoadResponse.addMalId(id: Int?) {
            if (id != null) this.syncData["MyAnimeList"] = id.toString()
        }

        fun LoadResponse.addMalId(id: String?) {
            if (id != null) this.syncData["MyAnimeList"] = id
        }

        fun LoadResponse.addImdbId(id: String?) {
            if (id != null) this.syncData["Imdb"] = id
        }

        fun LoadResponse.addImdbUrl(url: String?) {
            if (url != null) {
                val id = Regex("(tt\\d+)").find(url)?.groupValues?.getOrNull(1) ?: url
                this.syncData["Imdb"] = id
            }
        }

        fun LoadResponse.addTMDbId(id: String?) {
            if (id != null) this.syncData["Tmdb"] = id
        }

        fun LoadResponse.addTMDbId(id: Int?) {
            if (id != null) this.syncData["Tmdb"] = id.toString()
        }

        fun LoadResponse.addKitsuId(id: String?) {
            if (id != null) this.syncData["Kitsu"] = id
        }

        fun LoadResponse.addKitsuId(id: Int?) {
            if (id != null) this.syncData["Kitsu"] = id.toString()
        }

        fun LoadResponse.addSimklId(id: String?) {
            if (id != null) this.syncData["Simkl"] = id
        }

        fun LoadResponse.addSimklId(id: Int?) {
            if (id != null) this.syncData["Simkl"] = id.toString()
        }

        fun LoadResponse.addTraktId(id: String?) {
            if (id != null) this.syncData["Trakt"] = id
        }

        fun LoadResponse.addTraktId(id: Int?) {
            if (id != null) this.syncData["Trakt"] = id.toString()
        }

        fun LoadResponse.addActors(actors: List<String>?) {
            if (actors == null) return
            this.actors = actors.map { ActorData(Actor(it)) }
        }

        fun LoadResponse.addActorNames(actors: List<String>?) {
            addActors(actors)
        }

        fun LoadResponse.addDuration(duration: String?) {
            if (duration == null) return
            val mins = Regex("(\\d+)\\s*(?:min|m)").find(duration)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: duration.filter { it.isDigit() }.toIntOrNull()
            if (mins != null) this.duration = mins
        }

        var malIdPrefix: String? = "mal"
        var kitsuIdPrefix: String? = "kitsu"
        var aniListIdPrefix: String? = "anilist"
        var simklIdPrefix: String? = "simkl"

        fun readIdFromString(id: String): Map<SimklSyncServices, String> {
            if (id.startsWith("{") && id.endsWith("}")) {
                return try {
                    val map = mapper.readValue<Map<String, Any>>(id)
                    map.mapNotNull { (k, v) ->
                        val service = SimklSyncServices.entries.firstOrNull { it.originalName.equals(k, true) || it.name.equals(k, true) }
                        if (service != null) service to v.toString() else null
                    }.toMap()
                } catch (_: Throwable) {
                    emptyMap()
                }
            }
            val service = SimklSyncServices.entries.firstOrNull { id.startsWith(it.originalName, true) }
            if (service != null) {
                val cleanId = id.substringAfter("=").substringAfter(":")
                return mapOf(service to cleanId)
            }
            return mapOf(SimklSyncServices.Simkl to id)
        }
    }
}

enum class SimklSyncServices(val originalName: String) {
    Simkl("simkl"),
    Imdb("imdb"),
    Tmdb("tmdb"),
    Mal("mal"),
    AniList("anilist")
}

fun splitUrlParameters(url: String): Map<String, String> {
    val query = url.substringAfter("?", "").ifEmpty { url.substringAfter("#", "") }
    if (query.isEmpty()) return emptyMap()
    return query.split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else null
    }.toMap()
}

fun base64Encode(bytes: ByteArray): String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

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

data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
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
    override var uniqueUrl: String = "",
    var engName: String? = null,
    var jpnName: String? = null
) : LoadResponse

data class LiveStreamLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var dataUrl: String,
    override var type: TvType = TvType.Live,
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
) {
    var rating: Int?
        get() = score?.toInt(100)
        set(value) {
            score = Score.from100(value)
        }

    constructor(
        data: String,
        name: String?,
        season: Int?,
        episode: Int?,
        posterUrl: String?,
        rating: Int?,
        description: String?,
        date: Long?,
        runTime: Int?
    ) : this(data, name, season, episode, posterUrl, Score.from100(rating), description, date, runTime)
}

fun Episode.addDate(date: String?, format: String? = null) {
    if (date.isNullOrBlank()) return
    try {
        if (format != null) {
            val sdf = java.text.SimpleDateFormat(format, java.util.Locale.ROOT)
            this.date = sdf.parse(date)?.time
        } else {
            val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "MM-dd-yyyy", "yyyy/MM/dd", "dd/MM/yyyy", "d MMMM yyyy", "MMMM d, yyyy", "d MMM yyyy", "MMM d, yyyy")
            for (f in formats) {
                try {
                    val sdf = java.text.SimpleDateFormat(f, java.util.Locale.ROOT)
                    val parsed = sdf.parse(date)
                    if (parsed != null) {
                        this.date = parsed.time
                        break
                    }
                } catch (_: Throwable) {}
            }
        }
    } catch (_: Throwable) {}
}

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

@Keep
abstract class MainAPI {
    companion object {
        val settingsForProvider: SettingsJson = SettingsJson()
    }
    open var name: String = "Unknown Provider"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open var sourcePlugin: String? = null
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open val supportedSyncNames: Set<com.lagradost.cloudstream3.syncproviders.SyncIdName> = emptySet()
    open var hasMainPage: Boolean = false
    open var hasQuickSearch: Boolean = false
    open val hasDownloadSupport: Boolean = false
    open val mainPage: List<MainPageData> = emptyList()

    open suspend fun search(query: String): List<SearchResponse> = emptyList()
    open suspend fun search(query: String, page: Int): SearchResponseList? = null
    
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun loadMainPage(page: Int, name: String? = null): HomePageResponse? {
        val requests = if (name != null) {
            mainPage.filter { it.name == name }
        } else {
            mainPage
        }
        if (requests.isEmpty()) return null
        val lists = mutableListOf<HomePageList>()
        for (item in requests) {
            try {
                val req = MainPageRequest(item.name, item.data, item.horizontalImages)
                val res = getMainPage(page, req)
                if (res != null) {
                    lists.addAll(res.items)
                }
            } catch (t: Throwable) {
                // Ignore individual section failures
            }
        }
        return if (lists.isEmpty()) null else HomePageResponse(lists)
    }
    
    open suspend fun getLoadUrl(name: com.lagradost.cloudstream3.syncproviders.SyncIdName, id: String): String? = null

    open suspend fun load(url: String): LoadResponse? = null
    
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean = false
}

suspend fun MainAPI.searchSafe(query: String): List<SearchResponse> {
    val direct = runCatching { search(query) }.getOrNull()
    if (!direct.isNullOrEmpty()) return direct
    val paginated = runCatching { search(query, 1)?.items }.getOrNull()
    if (!paginated.isNullOrEmpty()) return paginated
    return direct ?: emptyList()
}

fun MainAPI.newMovieSearchResponse(name: String, url: String, type: TvType = TvType.Movie, fix: Boolean = true, initializer: MovieSearchResponse.() -> Unit = {}): MovieSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    return MovieSearchResponse(name, fixedUrl, this.name, type).apply(initializer)
}
fun MainAPI.newTvSeriesSearchResponse(name: String, url: String, type: TvType = TvType.TvSeries, fix: Boolean = true, initializer: TvSeriesSearchResponse.() -> Unit = {}): TvSeriesSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    return TvSeriesSearchResponse(name, fixedUrl, this.name, type).apply(initializer)
}
fun MainAPI.newAnimeSearchResponse(name: String, url: String, type: TvType = TvType.Anime, fix: Boolean = true, initializer: AnimeSearchResponse.() -> Unit = {}): AnimeSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    return AnimeSearchResponse(name, fixedUrl, this.name, type).apply(initializer)
}
fun MainAPI.newTorrentSearchResponse(name: String, url: String, type: TvType = TvType.Others, fix: Boolean = true, initializer: TorrentSearchResponse.() -> Unit = {}): TorrentSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    return TorrentSearchResponse(name, fixedUrl, this.name, type).apply(initializer)
}
fun MainAPI.newLiveSearchResponse(name: String, url: String, type: TvType = TvType.Live, fix: Boolean = true, initializer: LiveSearchResponse.() -> Unit = {}): LiveSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    return LiveSearchResponse(name, fixedUrl, this.name, type).apply(initializer)
}

suspend fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    dataUrl: Any,
    initializer: suspend LiveStreamLoadResponse.() -> Unit = {}
): LiveStreamLoadResponse {
    val dataStr = if (dataUrl is String) dataUrl else dataUrl.toJson()
    val res = LiveStreamLoadResponse(name, url, this.name, dataStr, type)
    res.initializer()
    return res
}

fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    dataUrl: Any,
    initializer: LiveStreamLoadResponse.() -> Unit = {}
): LiveStreamLoadResponse {
    val dataStr = if (dataUrl is String) dataUrl else dataUrl.toJson()
    return LiveStreamLoadResponse(name, url, this.name, dataStr, type).apply(initializer)
}

suspend fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    dataUrl: String,
    initializer: suspend LiveStreamLoadResponse.() -> Unit = {}
): LiveStreamLoadResponse {
    val res = LiveStreamLoadResponse(name, url, this.name, dataUrl, type)
    res.initializer()
    return res
}

fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    dataUrl: String,
    initializer: LiveStreamLoadResponse.() -> Unit = {}
): LiveStreamLoadResponse {
    return LiveStreamLoadResponse(name, url, this.name, dataUrl, type).apply(initializer)
}
suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: Any,
    initializer: suspend MovieLoadResponse.() -> Unit
): MovieLoadResponse {
    val dataStr = if (dataUrl is String) dataUrl else dataUrl.toJson()
    val res = MovieLoadResponse(name, url, this.name, type, dataStr)
    res.initializer()
    return res
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: suspend MovieLoadResponse.() -> Unit
): MovieLoadResponse {
    val res = MovieLoadResponse(name, url, this.name, type, dataUrl)
    res.initializer()
    return res
}

fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: Any,
    initializer: MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    val dataStr = if (dataUrl is String) dataUrl else dataUrl.toJson()
    return MovieLoadResponse(name, url, this.name, type, dataStr).apply(initializer)
}

fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    return MovieLoadResponse(name, url, this.name, type, dataUrl).apply(initializer)
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    initializer: suspend MovieLoadResponse.() -> Unit
): MovieLoadResponse {
    return newMovieLoadResponse(name, url, type, url as Any, initializer)
}

fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    initializer: MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    return newMovieLoadResponse(name, url, type, url as Any, initializer)
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    initializer: suspend TvSeriesLoadResponse.() -> Unit
): TvSeriesLoadResponse {
    val res = TvSeriesLoadResponse(name, url, this.name, type, episodes)
    res.initializer()
    return res
}

fun MainAPI.newTvSeriesLoadResponse(name: String, url: String, type: TvType, episodes: List<Episode>, initializer: TvSeriesLoadResponse.() -> Unit = {}): TvSeriesLoadResponse {
    return TvSeriesLoadResponse(name, url, this.name, type, episodes).apply(initializer)
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    initializer: suspend TvSeriesLoadResponse.() -> Unit
): TvSeriesLoadResponse {
    return newTvSeriesLoadResponse(name, url, type, emptyList(), initializer)
}

fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    initializer: TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse {
    return newTvSeriesLoadResponse(name, url, type, emptyList(), initializer)
}

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType,
    initializer: suspend AnimeLoadResponse.() -> Unit
): AnimeLoadResponse {
    val res = AnimeLoadResponse(name, url, this.name, type)
    res.initializer()
    return res
}

fun MainAPI.newAnimeLoadResponse(name: String, url: String, type: TvType, initializer: AnimeLoadResponse.() -> Unit = {}): AnimeLoadResponse {
    return AnimeLoadResponse(name, url, this.name, type).apply(initializer)
}

fun MainAPI.newEpisode(data: Any, initializer: Episode.() -> Unit = {}): Episode {
    val dataStr = if (data is String) data else data.toJson()
    return Episode(dataStr).apply(initializer)
}

fun MainAPI.newEpisode(data: String, initializer: Episode.() -> Unit = {}): Episode {
    return Episode(data).apply(initializer)
}

fun newEpisode(data: Any, initializer: Episode.() -> Unit = {}): Episode {
    val dataStr = if (data is String) data else data.toJson()
    return Episode(dataStr).apply(initializer)
}

fun newEpisode(data: String, initializer: Episode.() -> Unit = {}): Episode {
    return Episode(data).apply(initializer)
}
fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean? = false): HomePageResponse {
    return HomePageResponse(listOf(HomePageList(name, list)), hasNext ?: false)
}
fun newHomePageResponse(list: HomePageList, hasNext: Boolean? = false): HomePageResponse {
    return HomePageResponse(listOf(list), hasNext ?: false)
}
fun newHomePageResponse(items: List<HomePageList>, hasNext: Boolean? = false): HomePageResponse {
    return HomePageResponse(items, hasNext ?: false)
}
fun newSearchResponseList(items: List<SearchResponse>, hasNext: Boolean? = false): SearchResponseList {
    return SearchResponseList(items, hasNext ?: false)
}

fun getQualityFromString(string: String?): SearchQuality? {
    if (string == null) return null
    val s = string.uppercase()
    return when {
        s.contains("4K") || s.contains("UHD") -> SearchQuality.FourK
        s.contains("1080") || s.contains("FHD") -> SearchQuality.HD
        s.contains("720") || s.contains("HD") -> SearchQuality.HD
        s.contains("BLURAY") || s.contains("BLUE-RAY") || s.contains("BLU-RAY") -> SearchQuality.BlueRay
        s.contains("WEBRIP") || s.contains("WEB-DL") || s.contains("WEB") -> SearchQuality.WebRip
        s.contains("HD-CAM") || s.contains("HDCAM") -> SearchQuality.HdCam
        s.contains("CAM") || s.contains("CAMRIP") -> SearchQuality.Cam
        s.contains("DVD") || s.contains("DVDRIP") -> SearchQuality.DVD
        s.contains("SD") || s.contains("480") || s.contains("360") -> SearchQuality.SD
        s.contains("HQ") -> SearchQuality.HQ
        s.contains("TS") || s.contains("TELESYNC") -> SearchQuality.Telesync
        s.contains("HDR") -> SearchQuality.HDR
        else -> null
    }
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
fun LoadResponse.addTrailer(url: String?) {
    if (url.isNullOrBlank()) return
    this.trailers.add(TrailerData(url))
}
fun LoadResponse.addTrailer(trailer: TrailerData?) {
    if (trailer != null) this.trailers.add(trailer)
}
fun LoadResponse.addAniListId(id: Int?) {
    if (id != null) this.syncData["Anilist"] = id.toString()
}
fun LoadResponse.addAniListId(id: String?) {
    if (id != null) this.syncData["Anilist"] = id
}
fun LoadResponse.addMalId(id: Int?) {
    if (id != null) this.syncData["MyAnimeList"] = id.toString()
}
fun LoadResponse.addMalId(id: String?) {
    if (id != null) this.syncData["MyAnimeList"] = id
}
fun LoadResponse.addImdbId(id: String?) {
    if (id != null) this.syncData["Imdb"] = id
}
fun LoadResponse.addImdbUrl(url: String?) {
    if (url != null) {
        val id = Regex("(tt\\d+)").find(url)?.groupValues?.getOrNull(1) ?: url
        this.syncData["Imdb"] = id
    }
}
fun LoadResponse.addTMDbId(id: String?) {
    if (id != null) this.syncData["Tmdb"] = id
}
fun LoadResponse.addTMDbId(id: Int?) {
    if (id != null) this.syncData["Tmdb"] = id.toString()
}
fun LoadResponse.addKitsuId(id: String?) {
    if (id != null) this.syncData["Kitsu"] = id
}
fun LoadResponse.addKitsuId(id: Int?) {
    if (id != null) this.syncData["Kitsu"] = id.toString()
}
fun LoadResponse.addSimklId(id: String?) {
    if (id != null) this.syncData["Simkl"] = id
}
fun LoadResponse.addSimklId(id: Int?) {
    if (id != null) this.syncData["Simkl"] = id.toString()
}
fun LoadResponse.addTraktId(id: String?) {
    if (id != null) this.syncData["Trakt"] = id
}
fun LoadResponse.addTraktId(id: Int?) {
    if (id != null) this.syncData["Trakt"] = id.toString()
}
fun LoadResponse.addActors(actors: List<String>?) {
    if (actors == null) return
    this.actors = actors.map { ActorData(Actor(it)) }
}
fun LoadResponse.addDuration(duration: String?) {
    if (duration == null) return
    val mins = Regex("(\\d+)\\s*(?:min|m)").find(duration)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: duration.filter { it.isDigit() }.toIntOrNull()
    if (mins != null) this.duration = mins
}

fun String?.toRatingInt(): Int? {
    if (this == null) return null
    return (this.toDoubleOrNull()?.times(10))?.toInt()
}

fun fixTitle(title: String): String {
    return title.trim().replace(Regex("""\s+"""), " ")
}

fun getBaseUrl(url: String): String {
    return try {
        val uri = java.net.URI(url)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: url.substringBefore("/").substringBefore("?")
        "$scheme://$host"
    } catch (_: Throwable) {
        if (url.startsWith("http")) url.substringBefore("/", url) else url
    }
}

@JvmName("getBaseUrlExt")
fun String.getBaseUrl(): String = getBaseUrl(this)

fun updateUrl(url: String): String = url

fun SearchResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    if (url.isNullOrBlank()) return
    this.posterUrl = url
    if (headers != null) {
        this.posterHeaders = headers
    }
}

fun SearchResponse.addQuality(quality: String?) {
    if (quality.isNullOrBlank()) return
    this.quality = getQualityFromString(quality)
}

fun SearchResponse.addQuality(quality: SearchQuality?) {
    if (quality == null) return
    this.quality = quality
}

fun LoadResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    if (url.isNullOrBlank()) return
    this.posterUrl = url
    if (headers != null) {
        this.posterHeaders = headers
    }
}

fun hexToBytes(hex: String): ByteArray {
    val cleanHex = hex.trim().replace(" ", "").lowercase()
    val len = cleanHex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

fun bytesToHex(bytes: ByteArray): String {
    val hexArray = "0123456789abcdef".toCharArray()
    val hexChars = CharArray(bytes.size * 2)
    for (j in bytes.indices) {
        val v = bytes[j].toInt() and 0xFF
        hexChars[j * 2] = hexArray[v ushr 4]
        hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

fun capitalizeString(str: String): String {
    return capitalizeStringNullable(str) ?: str
}

fun capitalizeStringNullable(str: String?): String? {
    if (str == null) return null
    return str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
}



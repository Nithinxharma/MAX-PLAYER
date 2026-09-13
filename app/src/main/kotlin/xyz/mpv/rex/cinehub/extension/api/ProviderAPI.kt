package xyz.mpv.rex.cinehub.extension.api

/**
 * The base provider interface that all extensions must implement.
 * This mimics the structure used by CloudStream.
 */
interface MainAPI {
    val name: String
    val mainUrl: String
    val supportedTypes: Set<TvType>
    val lang: String
    val hasMainPage: Boolean

    suspend fun search(query: String): List<SearchResponse>
    suspend fun loadMainPage(page: Int, name: String): HomePageResponse?
    suspend fun load(url: String): LoadResponse?
    suspend fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean
}

enum class TvType {
    Movie, TvSeries, Anime, LiveTv, Others
}

open class SearchResponse(
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType,
    val posterUrl: String? = null,
    val id: Int? = null
)

class HomePageResponse(
    val items: List<HomePageList>
)

class HomePageList(
    val name: String,
    val list: List<SearchResponse>
)

abstract class LoadResponse(
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType,
    val posterUrl: String?,
    val year: Int?,
    val plot: String?
)

class MovieLoadResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType,
    val dataUrl: String,
    posterUrl: String? = null,
    year: Int? = null,
    plot: String? = null
) : LoadResponse(name, url, apiName, type, posterUrl, year, plot)

class TvSeriesLoadResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType,
    val episodes: List<Episode>,
    posterUrl: String? = null,
    year: Int? = null,
    plot: String? = null
) : LoadResponse(name, url, apiName, type, posterUrl, year, plot)

class Episode(
    val data: String,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null
)

class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

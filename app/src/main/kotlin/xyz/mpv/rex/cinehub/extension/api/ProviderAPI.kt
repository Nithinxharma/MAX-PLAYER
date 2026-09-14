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

/**
 * CineHub native provider interface offering unified abstraction
 * for external repositories, declarative providers, and CloudStream bridges.
 */
interface CineHubProvider {
    val id: String
    val name: String
    val version: String get() = "1.0.0"
    val author: String? get() = null
    val iconUrl: String? get() = null
    val description: String? get() = null
    val supportedTypes: Set<TvType> get() = setOf(TvType.Movie, TvType.TvSeries)
    val lang: String get() = "en"
    val hasMainPage: Boolean get() = true

    suspend fun search(query: String): List<CineHubSearchItem>
    suspend fun getHomePage(): List<CineHubHomePageList> = emptyList()
    suspend fun loadDetails(url: String): CineHubMediaDetails?
    suspend fun loadEpisodes(url: String): List<CineHubEpisode> = emptyList()
    suspend fun loadStreams(data: String): List<CineHubStreamLink>
    suspend fun loadLinks(
        data: String,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)? = null,
        callback: (CineHubStreamLink) -> Unit
    ): Boolean {
        val list = loadStreams(data)
        list.forEach(callback)
        return list.isNotEmpty()
    }
    suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> = emptyList()
}

data class CineHubSearchItem(
    val id: String,
    val title: String,
    val url: String,
    val providerId: String,
    val providerName: String,
    val posterUrl: String? = null,
    val type: TvType = TvType.Movie,
    val year: Int? = null,
    val rating: Double? = null
)

data class CineHubHomePageList(
    val title: String,
    val items: List<CineHubSearchItem>
)

data class CineHubMediaDetails(
    val id: String,
    val title: String,
    val url: String,
    val providerId: String,
    val providerName: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val type: TvType = TvType.Movie,
    val episodes: List<CineHubEpisode> = emptyList()
)

data class CineHubEpisode(
    val id: String,
    val name: String,
    val season: Int = 1,
    val episode: Int = 1,
    val data: String,
    val posterUrl: String? = null,
    val description: String? = null
)

data class CineHubStreamLink(
    val name: String,
    val url: String,
    val quality: String = "1080p",
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val referer: String = "",
    val qualityNumeric: Int = 1080,
    val streamType: String = "VIDEO",
    val host: String = ""
) {
    fun toStreamCandidate(): xyz.mpv.rex.cinehub.failover.StreamCandidate {
        val finalHeaders = if (referer.isNotBlank() && !headers.containsKey("Referer") && !headers.containsKey("referer")) {
            headers + ("Referer" to referer)
        } else headers
        return xyz.mpv.rex.cinehub.failover.StreamCandidate(
            url = url,
            name = name,
            quality = quality,
            isM3u8 = isM3u8,
            headers = finalHeaders,
            referer = referer,
            qualityNumeric = qualityNumeric,
            streamType = streamType,
            host = host
        )
    }
}

data class CineHubSubtitleTrack(
    val language: String,
    val url: String
)

/**
 * Adapter converting legacy CloudStream MainAPI into CineHubProvider.
 */
class MainApiProviderAdapter(private val api: MainAPI) : CineHubProvider {
    override val id: String = api.name.lowercase().replace("\\s+".toRegex(), "_")
    override val name: String = api.name
    override val supportedTypes: Set<TvType> = api.supportedTypes
    override val lang: String = api.lang
    override val hasMainPage: Boolean = api.hasMainPage

    override suspend fun search(query: String): List<CineHubSearchItem> {
        return api.search(query).map { item ->
            CineHubSearchItem(
                id = item.url,
                title = item.name,
                url = item.url,
                providerId = id,
                providerName = api.name,
                posterUrl = item.posterUrl,
                type = item.type
            )
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> {
        val page = api.loadMainPage(1, "") ?: return emptyList()
        return page.items.map { group ->
            CineHubHomePageList(
                title = group.name,
                items = group.list.map { item ->
                    CineHubSearchItem(
                        id = item.url,
                        title = item.name,
                        url = item.url,
                        providerId = id,
                        providerName = api.name,
                        posterUrl = item.posterUrl,
                        type = item.type
                    )
                }
            )
        }
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? {
        val res = api.load(url) ?: return null
        val episodes = if (res is TvSeriesLoadResponse) {
            res.episodes.mapIndexed { index, ep ->
                CineHubEpisode(
                    id = "$url#ep_$index",
                    name = ep.name ?: "Episode ${ep.episode ?: (index + 1)}",
                    season = ep.season ?: 1,
                    episode = ep.episode ?: (index + 1),
                    data = ep.data,
                    posterUrl = ep.posterUrl
                )
            }
        } else emptyList()

        return CineHubMediaDetails(
            id = res.url,
            title = res.name,
            url = res.url,
            providerId = id,
            providerName = api.name,
            posterUrl = res.posterUrl,
            overview = res.plot,
            year = res.year,
            type = res.type,
            episodes = episodes
        )
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> {
        val details = loadDetails(url)
        return details?.episodes ?: emptyList()
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> {
        val links = mutableListOf<CineHubStreamLink>()
        api.loadLinks(data, false) { extractor ->
            val qualityLabel = if (extractor.quality > 0) "${extractor.quality}p" else "Auto"
            links.add(
                CineHubStreamLink(
                    name = extractor.name,
                    url = extractor.url,
                    quality = qualityLabel,
                    isM3u8 = extractor.isM3u8,
                    headers = extractor.headers,
                    referer = extractor.referer,
                    qualityNumeric = extractor.quality,
                    streamType = if (extractor.isM3u8) "M3U8" else "VIDEO",
                    host = extractor.source
                )
            )
        }
        return links
    }

    override suspend fun loadLinks(
        data: String,
        subtitleCallback: ((CineHubSubtitleTrack) -> Unit)?,
        callback: (CineHubStreamLink) -> Unit
    ): Boolean {
        var foundAny = false
        api.loadLinks(data, false) { extractor ->
            foundAny = true
            val qualityLabel = if (extractor.quality > 0) "${extractor.quality}p" else "Auto"
            callback(
                CineHubStreamLink(
                    name = extractor.name,
                    url = extractor.url,
                    quality = qualityLabel,
                    isM3u8 = extractor.isM3u8,
                    headers = extractor.headers,
                    referer = extractor.referer,
                    qualityNumeric = extractor.quality,
                    streamType = if (extractor.isM3u8) "M3U8" else "VIDEO",
                    host = extractor.source
                )
            )
        }
        return foundAny
    }
}

package xyz.mpv.rex.cinehub.extension.api

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink

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
    val headers: Map<String, String> = emptyMap()
)

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
        return try {
            api.search(query)?.map { item ->
                CineHubSearchItem(
                    id = item.url,
                    title = item.name,
                    url = item.url,
                    providerId = id,
                    providerName = api.name,
                    posterUrl = item.posterUrl,
                    type = item.type ?: TvType.Movie
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> {
        if (!api.hasMainPage) return emptyList()
        val lists = mutableListOf<CineHubHomePageList>()
        try {
            val mainPages = api.mainPage
            for (pageData in mainPages) {
                try {
                    val req = MainPageRequest(
                        name = pageData.name,
                        data = pageData.data,
                        horizontalImages = pageData.horizontalImages
                    )
                    val resp = api.getMainPage(1, req)
                    resp?.items?.forEach { hpList ->
                        val searchItems = hpList.list.map { sr ->
                            CineHubSearchItem(
                                id = sr.url,
                                title = sr.name,
                                url = sr.url,
                                providerId = id,
                                providerName = api.name,
                                posterUrl = sr.posterUrl,
                                type = sr.type ?: TvType.Movie
                            )
                        }
                        if (searchItems.isNotEmpty()) {
                            lists.add(CineHubHomePageList(title = hpList.name, items = searchItems))
                        }
                    }
                } catch (_: Exception) {
                    // Skip failing individual section
                }
            }
        } catch (_: Exception) {
        }
        return lists
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? {
        val res = try {
            api.load(url)
        } catch (e: Exception) {
            null
        } ?: return null

        val episodes = when (res) {
            is TvSeriesLoadResponse -> {
                res.episodes.mapIndexed { index, ep ->
                    CineHubEpisode(
                        id = "$url#ep_$index",
                        name = ep.name ?: "Episode ${ep.episode ?: (index + 1)}",
                        season = ep.season ?: 1,
                        episode = ep.episode ?: (index + 1),
                        data = ep.data,
                        posterUrl = ep.posterUrl,
                        description = ep.description
                    )
                }
            }
            is AnimeLoadResponse -> {
                val allEps = mutableListOf<CineHubEpisode>()
                var counter = 1
                res.episodes.forEach { (status, epList) ->
                    epList.forEachIndexed { index, ep ->
                        allEps.add(
                            CineHubEpisode(
                                id = "$url#anime_${status.name}_$index",
                                name = ep.name ?: "Episode ${ep.episode ?: counter}",
                                season = ep.season ?: 1,
                                episode = ep.episode ?: counter,
                                data = ep.data,
                                posterUrl = ep.posterUrl ?: res.posterUrl,
                                description = ep.description
                            )
                        )
                        counter++
                    }
                }
                allEps
            }
            is MovieLoadResponse -> {
                listOf(
                    CineHubEpisode(
                        id = "$url#movie",
                        name = res.name,
                        season = 1,
                        episode = 1,
                        data = res.dataUrl
                    )
                )
            }
            is LiveStreamLoadResponse -> {
                listOf(
                    CineHubEpisode(
                        id = "$url#live",
                        name = res.name,
                        season = 1,
                        episode = 1,
                        data = res.dataUrl
                    )
                )
            }
            else -> emptyList()
        }

        val castNames = runCatching {
            res.actors?.map { it.actor.name } ?: emptyList()
        }.getOrDefault(emptyList())

        val mediaRating = runCatching { res.score?.toDouble(1) }.getOrNull()

        return CineHubMediaDetails(
            id = res.url,
            title = res.name,
            url = res.url,
            providerId = id,
            providerName = api.name,
            posterUrl = res.posterUrl,
            backdropUrl = res.backgroundPosterUrl,
            overview = res.plot,
            year = res.year,
            rating = mediaRating,
            genres = res.tags ?: emptyList(),
            cast = castNames,
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
        try {
            api.loadLinks(data, false, { _ -> 
                // Subtitles handled in loadSubtitles
            }, { extractor ->
                links.add(
                    CineHubStreamLink(
                        name = extractor.name,
                        url = extractor.url,
                        quality = extractor.quality.toString(),
                        isM3u8 = extractor.isM3u8,
                        headers = extractor.headers ?: emptyMap()
                    )
                )
            })
        } catch (e: Exception) {
            // Ignore
        }
        return links
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> {
        val subs = mutableListOf<CineHubSubtitleTrack>()
        try {
            api.loadLinks(data, false, { subtitle ->
                if (!subtitle.url.isNullOrBlank()) {
                    subs.add(CineHubSubtitleTrack(language = subtitle.lang ?: "en", url = subtitle.url))
                }
            }, { _ -> })
        } catch (e: Exception) {
            // Ignore
        }
        return subs
    }
}

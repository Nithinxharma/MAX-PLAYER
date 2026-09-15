cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/api/ProviderAPI.kt
package xyz.mpv.rex.cinehub.extension.api

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
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
        return api.search(query)?.map { item ->
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
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> {
        return emptyList()
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
        } else if (res is MovieLoadResponse) {
            listOf(CineHubEpisode(
                id = "$url#movie",
                name = "Movie",
                season = 1,
                episode = 1,
                data = res.dataUrl
            ))
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
        api.loadLinks(data, false, { subtitle -> 
            // handle subtitle
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
        return links
    }
}
INNER_EOF

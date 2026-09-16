package xyz.mpv.rex.cinehub.extension.api

import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.MainAPI as CsMainAPI
import com.lagradost.cloudstream3.TvType as CsTvType

/**
 * Adapter converting real CloudStream MainAPI (com.lagradost.cloudstream3.MainAPI)
 * into native CineHubProvider for unified search, metadata and playback.
 */
class CloudstreamMainApiAdapter(private val api: CsMainAPI) : CineHubProvider {
    override val id: String = "cs3_${api.name.lowercase().replace("\\s+".toRegex(), "_")}"
    override val name: String = api.name
    override val supportedTypes: Set<TvType> = api.supportedTypes.map { it.toRexType() }.toSet()
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
                type = item.type?.toRexType() ?: TvType.Movie
            )
        }
    }

    override suspend fun getHomePage(): List<CineHubHomePageList> {
        val page = api.loadMainPage(1, null) ?: return emptyList()
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
                        type = item.type?.toRexType() ?: TvType.Movie
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
                    posterUrl = ep.posterUrl,
                    description = ep.description
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
            type = res.type.toRexType(),
            episodes = episodes
        )
    }

    override suspend fun loadEpisodes(url: String): List<CineHubEpisode> {
        val details = loadDetails(url)
        return details?.episodes ?: emptyList()
    }

    override suspend fun loadStreams(data: String): List<CineHubStreamLink> {
        val links = mutableListOf<CineHubStreamLink>()
        api.loadLinks(data, false, subtitleCallback = {}) { extractor ->
            links.add(
                CineHubStreamLink(
                    name = extractor.name,
                    url = extractor.url,
                    quality = "${extractor.quality}p",
                    isM3u8 = extractor.isM3u8,
                    headers = buildMap {
                        if (extractor.referer.isNotBlank()) put("Referer", extractor.referer)
                        putAll(extractor.headers)
                    }
                )
            )
        }
        return links
    }

    private fun CsTvType.toRexType(): TvType = when (this) {
        CsTvType.Movie -> TvType.Movie
        CsTvType.TvSeries -> TvType.TvSeries
        CsTvType.Anime -> TvType.Anime
        CsTvType.AnimeMovie -> TvType.Movie
        CsTvType.OVA -> TvType.Anime
        CsTvType.Cartoon -> TvType.TvSeries
        CsTvType.Documentary -> TvType.Movie
        CsTvType.AsianDrama -> TvType.TvSeries
        CsTvType.Live -> TvType.LiveTv
        CsTvType.NSFW -> TvType.Others
        CsTvType.Others -> TvType.Others
    }
}

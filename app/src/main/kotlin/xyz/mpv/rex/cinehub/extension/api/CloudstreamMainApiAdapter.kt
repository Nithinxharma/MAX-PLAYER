package xyz.mpv.rex.cinehub.extension.api

import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.searchSafe
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
        val results = api.searchSafe(query)
        return results.map { item ->
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
        val lists = mutableListOf<CineHubHomePageList>()
        
        // 1. Try standard loadMainPage(1, null)
        val page = runCatching { api.loadMainPage(1, null) }.getOrNull()
        if (page != null && page.items.isNotEmpty()) {
            for (group in page.items) {
                if (group.list.isNotEmpty()) {
                    lists.add(
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
                    )
                }
            }
        }

        // 2. If lists is empty, query each entry in api.mainPage
        if (lists.isEmpty() && api.mainPage.isNotEmpty()) {
            for (item in api.mainPage) {
                runCatching {
                    val req = com.lagradost.cloudstream3.MainPageRequest(item.name, item.data, item.horizontalImages)
                    val res = api.getMainPage(1, req)
                    res?.items?.forEach { group ->
                        if (group.list.isNotEmpty()) {
                            val groupTitle = if (group.name.isNotBlank()) group.name else item.name
                            lists.add(
                                CineHubHomePageList(
                                    title = groupTitle,
                                    items = group.list.map { si ->
                                        CineHubSearchItem(
                                            id = si.url,
                                            title = si.name,
                                            url = si.url,
                                            providerId = id,
                                            providerName = api.name,
                                            posterUrl = si.posterUrl,
                                            type = si.type?.toRexType() ?: TvType.Movie
                                        )
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }

        return lists
    }

    override suspend fun loadDetails(url: String): CineHubMediaDetails? {
        val res = api.load(url) ?: return null
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
            is com.lagradost.cloudstream3.AnimeLoadResponse -> {
                val allEps = res.episodes.values.flatten().distinctBy { it.data }
                allEps.mapIndexed { index, ep ->
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
            else -> emptyList()
        }

        val streamData = if (res is MovieLoadResponse) res.dataUrl else res.url

        return CineHubMediaDetails(
            id = res.url,
            title = res.name,
            url = streamData,
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
        runCatching {
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
        }

        // Fallback 1: Attempt direct loadExtractor on data if links is empty and data is a valid URL
        if (links.isEmpty() && (data.startsWith("http://") || data.startsWith("https://"))) {
            runCatching {
                com.lagradost.cloudstream3.utils.loadExtractor(data, subtitleCallback = {}) { extractor ->
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
            }
        }

        // Fallback 2: If still empty and data is a direct HTTP(S) URL, wrap as real direct stream link
        if (links.isEmpty() && (data.startsWith("http://") || data.startsWith("https://"))) {
            links.add(
                CineHubStreamLink(
                    name = api.name,
                    url = data,
                    quality = "Auto",
                    isM3u8 = data.contains(".m3u8") || data.contains("m3u8"),
                    headers = emptyMap()
                )
            )
        }

        return links
    }

    override suspend fun loadSubtitles(data: String): List<CineHubSubtitleTrack> {
        val subs = mutableListOf<CineHubSubtitleTrack>()
        api.loadLinks(data, false, subtitleCallback = { sub ->
            subs.add(
                CineHubSubtitleTrack(
                    language = sub.lang,
                    url = sub.url
                )
            )
        }) { _ -> }
        return subs
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

package com.lagradost.cloudstream3

import androidx.annotation.Keep
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Keep
open class MainAPI {
    open var name: String = "Unknown"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open var supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open var hasMainPage: Boolean = false
    open var hasQuickSearch: Boolean = false
    open var hasChromecastSupport: Boolean = true
    open var hasDownloadSupport: Boolean = true
    open var vpnStatus: VPNStatus = VPNStatus.None
    open val providerType: ProviderType = ProviderType.PluginProvider

    open val mainPage: List<MainPageData> = emptyList()

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return null
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        return null
    }

    open suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
    }

    open suspend fun load(url: String): LoadResponse? {
        return null
    }

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }

    open fun fixUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("/")) {
            val base = if (mainUrl.endsWith("/")) mainUrl.dropLast(1) else mainUrl
            return "$base$url"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val base = if (mainUrl.endsWith("/")) mainUrl else "$mainUrl/"
            return "$base$url"
        }
        return url
    }

    open fun fixUrlNull(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return fixUrl(url)
    }
}

// Extension Builder Functions for MainAPI
fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    builder: MovieSearchResponse.() -> Unit = {}
): MovieSearchResponse {
    return MovieSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type
    ).apply(builder)
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    builder: TvSeriesSearchResponse.() -> Unit = {}
): TvSeriesSearchResponse {
    return TvSeriesSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type
    ).apply(builder)
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    builder: AnimeSearchResponse.() -> Unit = {}
): AnimeSearchResponse {
    return AnimeSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type
    ).apply(builder)
}

fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    dataUrl: String,
    builder: MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    return MovieLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type,
        dataUrl = dataUrl
    ).apply(builder)
}

fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    episodes: List<Episode>,
    builder: TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse {
    return TvSeriesLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type,
        episodes = episodes
    ).apply(builder)
}

fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    builder: AnimeLoadResponse.() -> Unit = {}
): AnimeLoadResponse {
    return AnimeLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type
    ).apply(builder)
}

fun MainAPI.newHomePageResponse(
    items: List<HomePageList>,
    hasNext: Boolean = false
): HomePageResponse {
    return HomePageResponse(items, hasNext)
}

fun MainAPI.newHomePageResponse(
    item: HomePageList,
    hasNext: Boolean = false
): HomePageResponse {
    return HomePageResponse(listOf(item), hasNext)
}

fun newEpisode(
    data: String,
    builder: Episode.() -> Unit = {}
): Episode {
    return Episode(data = data).apply(builder)
}

// Top-level variants for providers calling without receiver
fun newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = false,
    builder: MovieSearchResponse.() -> Unit = {}
): MovieSearchResponse {
    return MovieSearchResponse(name, url, "", type).apply(builder)
}

fun newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = false,
    builder: TvSeriesSearchResponse.() -> Unit = {}
): TvSeriesSearchResponse {
    return TvSeriesSearchResponse(name, url, "", type).apply(builder)
}

fun newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = false,
    builder: AnimeSearchResponse.() -> Unit = {}
): AnimeSearchResponse {
    return AnimeSearchResponse(name, url, "", type).apply(builder)
}

// Async Coroutines helpers used in CloudStream plugins (amap / parallelMap)
suspend fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

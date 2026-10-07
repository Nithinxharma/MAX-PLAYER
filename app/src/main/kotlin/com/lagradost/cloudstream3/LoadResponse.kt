package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.toJson

// Direct top-level helper builders for LoadResponses
suspend fun newMovieLoadResponse(
    api: MainAPI,
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    dataUrl: Any = url,
    initializer: suspend MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    return api.newMovieLoadResponse(name, url, type, dataUrl, initializer)
}

fun newMovieLoadResponse(
    api: MainAPI,
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    dataUrl: Any = url,
    initializer: MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    return api.newMovieLoadResponse(name, url, type, dataUrl, initializer)
}

suspend fun newTvSeriesLoadResponse(
    api: MainAPI,
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    episodes: List<Episode> = emptyList(),
    initializer: suspend TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse {
    return api.newTvSeriesLoadResponse(name, url, type, episodes, initializer)
}

fun newTvSeriesLoadResponse(
    api: MainAPI,
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    episodes: List<Episode> = emptyList(),
    initializer: TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse {
    return api.newTvSeriesLoadResponse(name, url, type, episodes, initializer)
}

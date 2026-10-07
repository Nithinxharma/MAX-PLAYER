package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.toJson

fun MainAPI.newEpisode(data: Any, initializer: Episode.() -> Unit = {}): Episode {
    val dataStr = if (data is String) data else data.toJson()
    return Episode(dataStr).apply(initializer)
}

fun MainAPI.newEpisode(data: String, initializer: Episode.() -> Unit = {}): Episode {
    return Episode(data).apply(initializer)
}

suspend fun MainAPI.newEpisode(data: Any, initializer: suspend Episode.() -> Unit): Episode {
    val dataStr = if (data is String) data else data.toJson()
    val ep = Episode(dataStr)
    ep.initializer()
    return ep
}

suspend fun MainAPI.newEpisode(data: String, initializer: suspend Episode.() -> Unit): Episode {
    val ep = Episode(data)
    ep.initializer()
    return ep
}

fun newEpisode(data: Any, initializer: Episode.() -> Unit = {}): Episode {
    val dataStr = if (data is String) data else data.toJson()
    return Episode(dataStr).apply(initializer)
}

fun newEpisode(data: String, initializer: Episode.() -> Unit = {}): Episode {
    return Episode(data).apply(initializer)
}

suspend fun newEpisode(data: Any, initializer: suspend Episode.() -> Unit): Episode {
    val dataStr = if (data is String) data else data.toJson()
    val ep = Episode(dataStr)
    ep.initializer()
    return ep
}

suspend fun newEpisode(data: String, initializer: suspend Episode.() -> Unit): Episode {
    val ep = Episode(data)
    ep.initializer()
    return ep
}

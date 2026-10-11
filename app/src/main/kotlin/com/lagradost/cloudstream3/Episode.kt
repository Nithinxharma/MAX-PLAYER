@file:JvmName("MainAPIKt")
@file:JvmMultifileClass
package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.toJson

fun MainAPI.newEpisode(
    url: String,
    initializer: Episode.() -> Unit = {},
    fix: Boolean = false,
): Episode {
    val builder = Episode(data = if (fix) fixUrl(url) else url)
    builder.initializer()
    return builder
}

fun <T : Any> MainAPI.newEpisode(
    data: T,
    initializer: Episode.() -> Unit = {}
): Episode {
    if (data is String) return newEpisode(url = data, initializer = initializer)
    return newEpisode(url = data.toJson(), initializer = initializer)
}

fun newEpisode(data: String, initializer: Episode.() -> Unit = {}): Episode {
    return Episode(data).apply(initializer)
}

fun <T : Any> newEpisode(data: T, initializer: Episode.() -> Unit = {}): Episode {
    val dataStr = if (data is String) data else data.toJson()
    return Episode(dataStr).apply(initializer)
}

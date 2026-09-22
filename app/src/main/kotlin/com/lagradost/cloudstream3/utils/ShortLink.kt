package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.app

object ShortLink {
    suspend fun unshorten(url: String): String {
        return try {
            val response = app.get(url, allowRedirects = true)
            response.url
        } catch (_: Throwable) {
            url
        }
    }

    suspend fun unshortenUrl(url: String): String = unshorten(url)
}

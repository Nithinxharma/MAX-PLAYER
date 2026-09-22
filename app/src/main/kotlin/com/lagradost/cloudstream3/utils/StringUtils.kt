package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Prerelease
import java.net.URLDecoder
import java.net.URLEncoder

object StringUtils {
    fun String.decodeUrl(): String {
        return try {
            URLDecoder.decode(this, "UTF-8")
        } catch (_: Exception) {
            this
        }
    }

    fun String.encodeUrl(): String {
        return try {
            URLEncoder.encode(this, "UTF-8")
        } catch (_: Exception) {
            this
        }
    }

    @Deprecated(
        message = "Use encodeUrl instead.",
        replaceWith = ReplaceWith("this.encodeUrl()"),
        level = DeprecationLevel.WARNING,
    )
    fun String.encodeUri(): String = encodeUrl()

    @Deprecated(
        message = "Use decodeUrl instead.",
        replaceWith = ReplaceWith("this.decodeUrl()"),
        level = DeprecationLevel.WARNING,
    )
    fun String.decodeUri(): String = decodeUrl()
}

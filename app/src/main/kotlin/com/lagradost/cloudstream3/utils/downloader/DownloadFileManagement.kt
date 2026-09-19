package com.lagradost.cloudstream3.utils.downloader

object DownloadFileManagement {
    fun sanitizeFilename(name: String, allowSpace: Boolean = true): String {
        val sanitized = if (allowSpace) {
            name.replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
        } else {
            name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        }
        return sanitized.trim().ifEmpty { "file" }
    }
}

package com.lagradost.cloudstream3.utils

import android.content.Context

object DataStoreHelper {
    fun <T> Context.setKey(folder: String, key: String, value: T) {
        DataStore.setKey(folder, key, value)
    }

    fun <T> Context.setKey(key: String, value: T) {
        DataStore.setKey(key, value)
    }

    fun <T> Context.getKey(folder: String, key: String, default: T): T {
        return DataStore.getKey(folder, key, default)
    }

    fun <T> Context.getKey(key: String, default: T): T {
        return DataStore.getKey(key, default)
    }

    fun Context.removeKey(folder: String, key: String) {
        DataStore.removeKey(folder, key)
    }

    var currentAccount: String = "default"

    fun Int?.toYear(): java.util.Date? = this?.let {
        try {
            java.util.Calendar.getInstance().apply {
                clear()
                set(java.util.Calendar.YEAR, it)
            }.time
        } catch (_: Throwable) { null }
    }

    fun getAllFavorites(): List<BookmarkedData> = emptyList()
    fun getAllWatchStateIds(): List<Int>? = emptyList()
    fun getResultWatchState(id: Int): com.lagradost.cloudstream3.ui.WatchType = com.lagradost.cloudstream3.ui.WatchType.NONE
    fun getBookmarkedData(id: Int): BookmarkedData? = null
    fun getAllSubscriptions(): List<BookmarkedData> = emptyList()

    data class BookmarkedData(
        val id: Int? = null,
        val name: String = "",
        val url: String = "",
        val apiName: String = "",
        val type: com.lagradost.cloudstream3.TvType? = null,
        val posterUrl: String? = null,
        val posterHeaders: Map<String, String>? = null,
        val quality: com.lagradost.cloudstream3.SearchQuality? = null,
        val score: com.lagradost.cloudstream3.Score? = null,
        val plot: String? = null
    ) {
        fun toLibraryItem(syncId: String = id?.toString() ?: ""): com.lagradost.cloudstream3.syncproviders.SyncAPI.LibraryItem {
            return com.lagradost.cloudstream3.syncproviders.SyncAPI.LibraryItem(
                name = name,
                url = url,
                syncId = syncId,
                episodesCompleted = null,
                episodesTotal = null,
                personalRating = score,
                lastUpdatedUnixTime = System.currentTimeMillis() / 1000L,
                apiName = apiName,
                type = type,
                posterUrl = posterUrl,
                posterHeaders = posterHeaders,
                quality = quality,
                releaseDate = null,
                id = id,
                plot = plot,
                score = score
            )
        }
    }
}

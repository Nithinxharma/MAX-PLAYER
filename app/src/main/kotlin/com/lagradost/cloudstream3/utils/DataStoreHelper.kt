package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.SyncWatchType

object DataStoreHelper {
    private const val FOLDER_WATCH_STATE = "watch_state"
    private const val FOLDER_BOOKMARKS = "bookmarks"
    private const val KEY_BOOKMARK_INDEX = "bookmark_index_list"

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

    // --- Watch State Management ---
    fun getResultWatchState(id: Int): WatchType {
        val stateId = DataStore.getKey(FOLDER_WATCH_STATE, id.toString(), WatchType.NONE.internalId)
        return WatchType.entries.firstOrNull { it.internalId == stateId } ?: WatchType.NONE
    }

    fun getResultWatchState(url: String): WatchType {
        if (url.isBlank()) return WatchType.NONE
        val stateId = DataStore.getKey(FOLDER_WATCH_STATE, url, WatchType.NONE.internalId)
        return WatchType.entries.firstOrNull { it.internalId == stateId } ?: WatchType.NONE
    }

    fun setResultWatchState(id: Int, watchType: WatchType) {
        if (watchType == WatchType.NONE) {
            DataStore.removeKey(FOLDER_WATCH_STATE, id.toString())
        } else {
            DataStore.setKey(FOLDER_WATCH_STATE, id.toString(), watchType.internalId)
        }
    }

    fun setResultWatchState(url: String, watchType: WatchType) {
        if (url.isBlank()) return
        if (watchType == WatchType.NONE) {
            DataStore.removeKey(FOLDER_WATCH_STATE, url)
        } else {
            DataStore.setKey(FOLDER_WATCH_STATE, url, watchType.internalId)
        }
    }

    // --- Bookmark Data Management ---
    fun getBookmarkedData(id: Int): BookmarkedData? {
        val json = DataStore.getKey<String?>(FOLDER_BOOKMARKS, id.toString(), null)
        return tryParseJson(json)
    }

    fun getBookmarkedData(url: String): BookmarkedData? {
        if (url.isBlank()) return null
        val json = DataStore.getKey<String?>(FOLDER_BOOKMARKS, url, null)
        return tryParseJson(json)
    }

    fun setBookmarkedData(data: BookmarkedData, watchType: WatchType = WatchType.WATCHING) {
        val key = data.url.ifBlank { data.id?.toString() ?: "" }
        if (key.isBlank()) return

        val json = data.toJson()
        DataStore.setKey(FOLDER_BOOKMARKS, key, json)
        if (data.id != null) {
            DataStore.setKey(FOLDER_BOOKMARKS, data.id.toString(), json)
        }
        setResultWatchState(key, watchType)
        if (data.id != null) {
            setResultWatchState(data.id, watchType)
        }

        // Maintain index list of bookmarked keys
        val indexList = getBookmarkIndexList().toMutableSet()
        indexList.add(key)
        DataStore.setKey(FOLDER_BOOKMARKS, KEY_BOOKMARK_INDEX, indexList.toList().toJson())
    }

    fun removeBookmark(url: String, id: Int? = null) {
        if (url.isNotBlank()) {
            DataStore.removeKey(FOLDER_BOOKMARKS, url)
            DataStore.removeKey(FOLDER_WATCH_STATE, url)
        }
        if (id != null) {
            DataStore.removeKey(FOLDER_BOOKMARKS, id.toString())
            DataStore.removeKey(FOLDER_WATCH_STATE, id.toString())
        }

        val indexList = getBookmarkIndexList().toMutableList()
        indexList.remove(url)
        if (id != null) indexList.remove(id.toString())
        DataStore.setKey(FOLDER_BOOKMARKS, KEY_BOOKMARK_INDEX, indexList.toJson())
    }

    private fun getBookmarkIndexList(): List<String> {
        val json = DataStore.getKey<String?>(FOLDER_BOOKMARKS, KEY_BOOKMARK_INDEX, null)
        return tryParseJson<List<String>>(json) ?: emptyList()
    }

    fun getAllFavorites(): List<BookmarkedData> {
        val keys = getBookmarkIndexList()
        val list = mutableListOf<BookmarkedData>()
        for (key in keys) {
            val data = getBookmarkedData(key)
            if (data != null) {
                list.add(data)
            }
        }
        return list.distinctBy { it.url.ifBlank { it.id?.toString() ?: "" } }
    }

    fun getAllWatchStateIds(): List<Int>? {
        return getAllFavorites().mapNotNull { it.id }
    }

    fun getAllSubscriptions(): List<BookmarkedData> = getAllFavorites()

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


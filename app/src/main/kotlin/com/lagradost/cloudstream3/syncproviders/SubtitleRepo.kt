package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api) {
    val mainUrl: String get() = api.mainUrl
    private val mutex = Mutex()
    private val searchCache = HashMap<SubtitleSearch, List<SubtitleEntity>>()

    suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? = mutex.withLock {
        searchCache[query]?.let { return it }
        val res = try {
            api.search(query)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
        if (res != null) {
            searchCache[query] = res
        }
        return res
    }

    suspend fun load(data: SubtitleEntity): SubtitleResource? {
        return try {
            api.load(data)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}

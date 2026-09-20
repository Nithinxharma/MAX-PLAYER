package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ui.SyncWatchType

class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val mainUrl: String get() = api.mainUrl

    suspend fun search(query: String): List<SyncAPI.SyncSearchResult>? {
        return try {
            api.search(query)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getStatus(id: String): SyncAPI.AbstractSyncStatus? {
        return try {
            api.getStatus(id)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    suspend fun score(id: String, status: SyncAPI.AbstractSyncStatus): Boolean {
        return try {
            api.score(id, status)
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getResult(id: String): SyncAPI.SyncResult? {
        return try {
            api.getResult(id)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getPersonalLibrary(): List<SyncAPI.LibraryList>? {
        return try {
            api.getPersonalLibrary()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}

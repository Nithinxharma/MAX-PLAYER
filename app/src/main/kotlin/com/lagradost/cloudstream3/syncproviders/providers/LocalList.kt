package com.lagradost.cloudstream3.syncproviders.providers

import xyz.mpv.rex.R
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.txt

class LocalList : SyncAPI() {
    override val name = "Local List"
    override val idPrefix = "locallist"
    override val requiresLogin = false
    override val syncIdName = SyncIdName.LocalList

    override suspend fun getStatus(id: String): AbstractSyncStatus? {
        val intId = id.toIntOrNull() ?: return null
        val watchType = DataStoreHelper.getResultWatchState(intId)
        val isFav = DataStoreHelper.getAllFavorites().any { it.id == intId }
        val syncType = when (watchType) {
            WatchType.WATCHING -> SyncWatchType.WATCHING
            WatchType.COMPLETED -> SyncWatchType.COMPLETED
            WatchType.ONHOLD -> SyncWatchType.ONHOLD
            WatchType.DROPPED -> SyncWatchType.DROPPED
            WatchType.PLANTOWATCH -> SyncWatchType.PLANTOWATCH
            WatchType.NONE -> SyncWatchType.NONE
        }
        return AbstractSyncStatus(
            status = syncType,
            isFavorite = isFav
        )
    }

    override suspend fun getPersonalLibrary(): List<LibraryList> {
        val favs = DataStoreHelper.getAllFavorites().map { it.toLibraryItem() }
        val subs = DataStoreHelper.getAllSubscriptions().map { it.toLibraryItem() }
        return listOf(
            LibraryList(
                name = txt(R.string.favorites_list_name),
                items = favs,
                syncId = "favorites"
            ),
            LibraryList(
                name = txt(R.string.subscription_list_name),
                items = subs,
                syncId = "subscriptions"
            )
        )
    }
}

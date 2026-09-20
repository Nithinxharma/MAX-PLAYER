package com.lagradost.cloudstream3.ui

import androidx.annotation.StringRes
import xyz.mpv.rex.R

enum class SyncWatchType(val internalId: Int, @StringRes val stringRes: Int = 0) {
    NONE(-1, R.string.none),
    WATCHING(0, R.string.type_watching),
    COMPLETED(1, R.string.type_completed),
    ONHOLD(2, R.string.type_on_hold),
    DROPPED(3, R.string.type_dropped),
    PLANTOWATCH(4, R.string.type_plan_to_watch),
    REWATCHING(5, R.string.type_re_watching);

    companion object {
        fun fromInternalId(id: Int?) = entries.firstOrNull { it.internalId == id } ?: NONE
    }
}

enum class WatchType(val internalId: Int, @StringRes val stringRes: Int = 0) {
    NONE(-1, R.string.none),
    WATCHING(0, R.string.type_watching),
    COMPLETED(1, R.string.type_completed),
    ONHOLD(2, R.string.type_on_hold),
    DROPPED(3, R.string.type_dropped),
    PLANTOWATCH(4, R.string.type_plan_to_watch);

    fun toLibraryItem(syncId: String): com.lagradost.cloudstream3.syncproviders.SyncAPI.LibraryItem? = null
}

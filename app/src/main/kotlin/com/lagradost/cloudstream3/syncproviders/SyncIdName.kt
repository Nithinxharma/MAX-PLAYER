package com.lagradost.cloudstream3.syncproviders

enum class SyncIdName {
    Anilist,
    MyAnimeList,
    Kitsu,
    Simkl,
    Shikimori,
    Trakt,
    Mal,
    OpenSubtitles,
    SubSource,
    Subscene,
    None
}

interface SyncAPI {
    val name: String
    val idPrefix: String
    val syncIdName: SyncIdName
    val mainUrl: String
    val hasOAuth: Boolean
}

abstract class AccountManager {
    open fun init() {}
}

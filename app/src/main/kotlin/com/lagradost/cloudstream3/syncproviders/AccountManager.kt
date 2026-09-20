package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.syncproviders.providers.*
import com.lagradost.cloudstream3.utils.DataStoreHelper

object AccountManager {
    const val ACCOUNT_KEY = "accounts"

    val aniListApi: AniListApi by lazy { AniListApi() }
    val malApi: MALApi by lazy { MALApi() }
    val kitsuApi: KitsuApi by lazy { KitsuApi() }
    val simklApi: SimklApi by lazy { SimklApi() }
    val localList: LocalList by lazy { LocalList() }

    val openSubtitlesApi: OpenSubtitlesApi by lazy { OpenSubtitlesApi() }
    val subDlApi: SubDlApi by lazy { SubDlApi() }
    val subSourceApi: SubSourceApi by lazy { SubSourceApi() }
    val addic7ed: Addic7ed by lazy { Addic7ed() }

    val syncProviders: Array<SyncAPI> by lazy {
        arrayOf(
            aniListApi,
            malApi,
            kitsuApi,
            simklApi,
            localList
        )
    }

    val subtitleProviders: Array<SubtitleAPI> by lazy {
        arrayOf(
            openSubtitlesApi,
            subDlApi,
            subSourceApi,
            addic7ed
        )
    }

    val backupProviders: Array<BackupAPI> by lazy {
        emptyArray()
    }

    val authProviders: Array<AuthAPI> by lazy {
        (syncProviders.map { it as AuthAPI } + subtitleProviders.map { it as AuthAPI }).toTypedArray()
    }

    val repos: Array<SyncRepo> by lazy {
        syncProviders.map { SyncRepo(it) }.toTypedArray()
    }

    val SubtitleRepos: Array<SubtitleRepo> by lazy {
        subtitleProviders.map { SubtitleRepo(it) }.toTypedArray()
    }

    fun getSyncRepo(id: String): SyncRepo? {
        return repos.firstOrNull { it.idPrefix == id }
    }

    fun getSubtitleRepo(id: String): SubtitleRepo? {
        return SubtitleRepos.firstOrNull { it.idPrefix == id }
    }

    fun getAccounts(api: AuthAPI): List<AuthData>? {
        return CloudStreamApp.getKey<List<AuthData>>("${api.idPrefix}/$ACCOUNT_KEY")
    }

    fun setAccounts(api: AuthAPI, accounts: List<AuthData>) {
        CloudStreamApp.setKey("${api.idPrefix}/$ACCOUNT_KEY", accounts)
    }

    fun addAccount(api: AuthAPI, data: AuthData) {
        val current = getAccounts(api)?.toMutableList() ?: mutableListOf()
        current.removeAll { it.accountIndex == data.accountIndex }
        current.add(data)
        setAccounts(api, current)
    }

    fun removeAccount(api: AuthAPI, index: Int) {
        val current = getAccounts(api)?.toMutableList() ?: return
        current.removeAll { it.accountIndex == index }
        setAccounts(api, current)
    }

    fun updateAccount(api: AuthAPI, data: AuthData) {
        addAccount(api, data)
    }

    fun currentAccount(api: AuthAPI): AuthData? {
        return getAccounts(api)?.firstOrNull()
    }
}

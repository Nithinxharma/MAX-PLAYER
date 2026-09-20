package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.CloudStreamApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class AuthRepo(open val api: AuthAPI) {
    val name: String get() = api.name
    val idPrefix: String get() = api.idPrefix
    val icon: Int? get() = api.icon
    val requiresLogin: Boolean get() = api.requiresLogin

    private val mutex = Mutex()

    open fun currentAccount(): AuthData? {
        return AccountManager.currentAccount(api)
    }

    open fun getAccounts(): List<AuthData>? {
        return AccountManager.getAccounts(api)
    }

    open fun addAccount(data: AuthData) {
        AccountManager.addAccount(api, data)
    }

    open fun removeAccount(index: Int) {
        AccountManager.removeAccount(api, index)
    }

    open suspend fun logout(index: Int = 0): Boolean {
        AccountManager.removeAccount(api, index)
        return api.logout(index)
    }

    open suspend fun initialize(): AuthLoginRequirement? {
        return api.initialize()
    }

    open suspend fun pollPin(): AuthLoginRequirement? {
        return api.pollPin()
    }

    open suspend fun handleRedirect(url: String): Boolean {
        return api.handleRedirect(url)
    }

    open fun loginInfo(): AuthLoginPage? {
        return api.loginInfo()
    }

    open suspend fun getAuthenticatedToken(): AuthToken? = mutex.withLock {
        val current = currentAccount() ?: return null
        val token = current.token
        if (api.isTokenExpired(token)) {
            val refreshed = api.refreshToken(token)
            if (refreshed != null) {
                val updated = current.copy(token = refreshed)
                AccountManager.updateAccount(api, updated)
                return refreshed
            }
        }
        return token
    }
}

class PlainAuthRepo(override val api: AuthAPI) : AuthRepo(api)

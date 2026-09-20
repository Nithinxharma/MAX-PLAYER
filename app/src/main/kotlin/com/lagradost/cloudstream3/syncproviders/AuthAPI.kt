package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.utils.AppUtils.toJson

data class AuthToken(
    @JsonProperty("token") val token: String,
    @JsonProperty("expiresUnix") val expiresUnix: Long? = null,
    @JsonProperty("refreshToken") val refreshToken: String? = null,
    @JsonProperty("tokenType") val tokenType: String? = null,
    @JsonProperty("issueDate") val issueDate: Long = unixTime,
)

data class AuthUser(
    @JsonProperty("name") val name: String,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("profilePicture") val profilePicture: String? = null,
)

data class AuthData(
    @JsonProperty("token") val token: AuthToken,
    @JsonProperty("user") val user: AuthUser,
    @JsonProperty("accountIndex") val accountIndex: Int,
)

data class AuthLoginPage(
    val url: String,
    val requiresPKCE: Boolean = false,
)

data class AuthPinData(
    val userCode: String,
    val pin: String,
    val authUrl: String,
    val expiresIn: Int,
)

data class AuthLoginRequirement(
    val pin: AuthPinData? = null,
    val isDone: Boolean = false,
    val isFailed: Boolean = false,
)

sealed class AuthLoginResponse {
    data class Success(val data: AuthData) : AuthLoginResponse()
    data class Failure(val error: String) : AuthLoginResponse()
    data class Pin(val pinData: AuthPinData) : AuthLoginResponse()
}

abstract class AuthAPI {
    abstract val name: String
    abstract val idPrefix: String
    open val icon: Int? = null
    open val requiresLogin: Boolean = true
    open val createAccountUrl: String? = null
    open val syncIdName: SyncIdName? = null

    open fun loginInfo(): AuthLoginPage? = null

    open suspend fun handleRedirect(url: String): Boolean = false

    open suspend fun initialize(): AuthLoginRequirement? = null

    open suspend fun pollPin(): AuthLoginRequirement? = null

    open suspend fun logout(index: Int = 0): Boolean = true

    open suspend fun refreshToken(token: AuthToken): AuthToken? = null

    open suspend fun getUser(token: AuthToken): AuthUser? = null

    open fun isTokenExpired(token: AuthToken): Boolean {
        val expires = token.expiresUnix ?: return false
        return unixTime >= expires
    }
}

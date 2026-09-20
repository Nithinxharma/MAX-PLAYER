package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class AniListApi : SyncAPI() {
    override val name = "AniList"
    override val idPrefix = "anilist"
    override val requiresLogin = true
    override val mainUrl = "https://anilist.co"
    override val syncIdName = SyncIdName.Anilist
    override val createAccountUrl = "https://anilist.co/signup"

    private val key: String = "1337"
    private val redirectUrl = "cloudstreamapp://anilist"

    override fun loginInfo(): AuthLoginPage {
        return AuthLoginPage(
            url = "https://anilist.co/api/v2/oauth/authorize?client_id=$key&response_type=token",
            requiresPKCE = false
        )
    }

    override suspend fun handleRedirect(url: String): Boolean {
        val params = splitUrlParameters(url)
        val token = params["access_token"] ?: return false
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 31536000L
        val authToken = AuthToken(
            token = token,
            expiresUnix = unixTime + expiresIn,
            tokenType = "Bearer"
        )
        val user = getUser(authToken) ?: return false
        val authData = AuthData(
            token = authToken,
            user = user,
            accountIndex = 0
        )
        AccountManager.addAccount(this, authData)
        return true
    }

    override suspend fun getUser(token: AuthToken): AuthUser? {
        val query = """
            query {
                Viewer {
                    id
                    name
                    avatar {
                        large
                    }
                }
            }
        """.trimIndent()
        val res = app.post(
            "https://graphql.anilist.co",
            headers = mapOf("Authorization" to "Bearer ${token.token}"),
            json = mapOf("query" to query)
        ).text
        val parsed = tryParseJson<AniListUserResponse>(res) ?: return null
        return AuthUser(
            name = parsed.data?.Viewer?.name ?: "User",
            id = parsed.data?.Viewer?.id?.toString(),
            profilePicture = parsed.data?.Viewer?.avatar?.large
        )
    }

    override suspend fun search(query: String): List<SyncSearchResult>? {
        val gql = """
            query (${'$'}query: String) {
                Page(page: 1, perPage: 20) {
                    media(search: ${'$'}query, type: ANIME) {
                        id
                        title {
                            userPreferred
                            romaji
                            english
                        }
                        coverImage {
                            extraLarge
                            large
                        }
                        averageScore
                        format
                    }
                }
            }
        """.trimIndent()
        val res = app.post(
            "https://graphql.anilist.co",
            json = mapOf("query" to gql, "variables" to mapOf("query" to query))
        ).text
        val parsed = tryParseJson<AniListSearchResponse>(res) ?: return null
        return parsed.data?.Page?.media?.map { media ->
            SyncSearchResult(
                name = media.title?.english ?: media.title?.userPreferred ?: media.title?.romaji ?: "",
                apiName = name,
                syncId = media.id.toString(),
                url = "$mainUrl/anime/${media.id}",
                posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                type = TvType.Anime,
                score = media.averageScore?.let { Score.from100(it) }
            )
        }
    }

    data class AniListUserResponse(val data: UserData?) {
        data class UserData(val Viewer: ViewerData?)
        data class ViewerData(val id: Int?, val name: String?, val avatar: AvatarData?)
        data class AvatarData(val large: String?)
    }

    data class AniListSearchResponse(val data: SearchPageData?) {
        data class SearchPageData(val Page: PageData?)
        data class PageData(val media: List<MediaItem>?)
        data class MediaItem(
            val id: Int,
            val title: TitleData?,
            val coverImage: CoverImageData?,
            val averageScore: Int?,
            val format: String?
        )
        data class TitleData(val userPreferred: String?, val romaji: String?, val english: String?)
        data class CoverImageData(val extraLarge: String?, val large: String?)
    }
}

package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.concurrent.ConcurrentHashMap

class AnimeSkipAuth : AuthAPI() {
    override val name = "AnimeSkip"
    override val idPrefix = "aniskip"
    override val requiresLogin = false

    companion object {
        const val BASE_CLIENT_ID = "ZGfO0sMF3eCwLYf8yMSCJjlynwNGRXWE"
        const val GRAPHQL_URL = "https://api.anime-skip.com/graphql"
    }

    data class LoginRoot(val data: LoginData)
    data class LoginData(val login: LoginResult)
    data class LoginResult(
        val authToken: String,
        val refreshToken: String,
        val account: Account
    )
    data class Account(
        val profileUrl: String? = null,
        val username: String? = null,
        val email: String? = null
    )
    data class ApiRoot(val data: ApiData)
    data class ApiData(val myApiClients: List<ApiClient>)
    data class ApiClient(val id: String)
    data class Payload(
        val profileUrl: String? = null,
        val username: String? = null,
        val email: String? = null,
        val clientId: String = BASE_CLIENT_ID
    )

    suspend fun login(usernameEmail: String, passwordHash: String): AuthToken? {
        val loginQuery = """
            {
              login(usernameEmail: "$usernameEmail", passwordHash: "$passwordHash") {
                authToken
                refreshToken
                account {
                  profileUrl
                  username
                  email
                }
              }
            }
        """.trimIndent()

        return try {
            val response = app.post(
                GRAPHQL_URL,
                json = mapOf("query" to loginQuery),
                headers = mapOf(
                    "Accept" to "*/*",
                    "content-type" to "application/json",
                    "X-Client-ID" to BASE_CLIENT_ID
                )
            ).parsed<LoginRoot>()

            val authToken = response.data.login.authToken
            val refreshToken = response.data.login.refreshToken
            val account = response.data.login.account

            val clientQuery = """
                {
                  myApiClients {
                    id
                  }
                }
            """.trimIndent()

            val clientResponse = app.post(
                GRAPHQL_URL,
                json = mapOf("query" to clientQuery),
                headers = mapOf(
                    "Accept" to "*/*",
                    "content-type" to "application/json",
                    "Authorization" to "Bearer $authToken",
                    "X-Client-ID" to BASE_CLIENT_ID
                )
            ).parsed<ApiRoot>()

            val clientId = clientResponse.data.myApiClients.firstOrNull()?.id ?: BASE_CLIENT_ID
            val payload = Payload(
                profileUrl = account.profileUrl,
                username = account.username,
                email = account.email,
                clientId = clientId
            )

            AuthToken(
                token = authToken,
                refreshToken = refreshToken
            )
        } catch (_: Throwable) {
            null
        }
    }
}

class AnimeSkip : SkipAPI() {
    override val name: String = "AnimeSkip"
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.OVA, TvType.Cartoon)

    companion object {
        const val MIN_LENGTH: Int = 3
        private val stripRegex = Regex("[ :\\-.!]")
        private val asciiRegex = Regex("[^a-zA-Z0-9 ]")

        fun stripName(name: String?): String? = name?.replace(stripRegex, "")?.lowercase()
        fun asciiName(name: String?): String? = name?.replace(asciiRegex, "")?.lowercase()
    }

    data class Root(@JsonProperty("data") val data: Data)
    data class Data(@JsonProperty("searchShows") val searchShows: List<SearchShow>)
    data class SearchShow(
        @JsonProperty("name") val name: String,
        @JsonProperty("originalName") val originalName: String? = null,
        @JsonProperty("seasonCount") val seasonCount: Long = 1L,
        @JsonProperty("episodeCount") val episodeCount: Long = 0L,
        @JsonProperty("baseDuration") val baseDuration: Double = 0.0,
        @JsonProperty("episodes") val episodes: List<Episode> = emptyList()
    )
    data class Episode(
        @JsonProperty("number") val number: String? = null,
        @JsonProperty("absoluteNumber") val absoluteNumber: String? = null,
        @JsonProperty("season") val season: String? = null,
        @JsonProperty("timestamps") val timestamps: List<Timestamp> = emptyList()
    )
    data class Timestamp(
        @JsonProperty("at") val at: Double,
        @JsonProperty("type") val type: Type
    )
    data class Type(@JsonProperty("name") val name: String)

    private val cache = ConcurrentHashMap<String, Data>()

    override suspend fun stamps(
        data: LoadResponse,
        episodeNum: Int,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        if (data !is AnimeLoadResponse && data !is TvSeriesLoadResponse) return null

        val query = """
            {
              searchShows(search: "${data.name.replace("\"", "\\\"")}", limit: 1) {
                name
                originalName
                seasonCount
                episodeCount
                episodes {
                  number
                  absoluteNumber
                  season
                  baseDuration
                  timestamps {
                    at
                    type {
                      name
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val rootData = cache[data.name] ?: run {
            try {
                val res = app.post(
                    AnimeSkipAuth.GRAPHQL_URL,
                    json = mapOf("query" to query),
                    headers = mapOf(
                        "Accept" to "*/*",
                        "content-type" to "application/json",
                        "X-Client-ID" to AnimeSkipAuth.BASE_CLIENT_ID
                    )
                ).parsed<Root>().data
                cache[data.name] = res
                res
            } catch (_: Throwable) {
                return null
            }
        }

        val show = rootData.searchShows.firstOrNull { candidate ->
            val a1 = asciiName(data.name)
            val a2 = asciiName(candidate.name)
            if (a1 != null && a1 == a2 && a1.length >= MIN_LENGTH) return@firstOrNull true

            val s1 = stripName(candidate.originalName)
            val s2 = if (data is AnimeLoadResponse) stripName(data.jpnName) else null
            if (s1 != null && s1 == s2 && s1.length >= MIN_LENGTH) return@firstOrNull true

            val e1 = if (data is AnimeLoadResponse) stripName(data.engName) else null
            if (e1 != null && e1 == a2 && e1.length >= MIN_LENGTH) return@firstOrNull true

            false
        } ?: rootData.searchShows.firstOrNull() ?: return null

        val epStr = episodeNum.toString()
        val showEpisode = show.episodes.firstOrNull {
            it.number == epStr || it.absoluteNumber == epStr
        } ?: return null

        val result = mutableListOf<SkipStamp>()
        var pending: SkipStamp? = null

        for (stamp in showEpisode.timestamps) {
            val startMs = (stamp.at * 1000.0).toLong()
            pending?.let { p ->
                result.add(p.copy(endMs = startMs))
            }

            val skipType = when (stamp.type.name) {
                "Intro", "New Intro", "Opening" -> SkipType.Intro
                "Credits", "Ending" -> SkipType.Credits
                "Preview" -> SkipType.Preview
                "Recap" -> SkipType.Recap
                "Mixed Credits", "Mixed Ending" -> SkipType.MixedEnding
                else -> null
            }

            if (skipType == null) {
                pending = null
                continue
            }

            pending = SkipStamp(skipType, startMs, startMs + 90_000L)
        }

        pending?.let { p ->
            val finalEndMs = if (episodeDurationMs > p.startMs) episodeDurationMs else p.endMs
            result.add(p.copy(endMs = finalEndMs))
        }

        return result
    }
}

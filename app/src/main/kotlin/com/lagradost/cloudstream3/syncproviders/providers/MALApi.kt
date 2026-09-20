package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class MALApi : SyncAPI() {
    override val name = "MyAnimeList"
    override val idPrefix = "mal"
    override val requiresLogin = true
    override val mainUrl = "https://myanimelist.net"
    override val syncIdName = SyncIdName.MyAnimeList
    override val createAccountUrl = "https://myanimelist.net/register.php"

    private val clientId = "6114d00ca681b77c011400d11e02975e"

    override fun loginInfo(): AuthLoginPage {
        return AuthLoginPage(
            url = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$clientId&code_challenge=cloudstream_app_challenge_1234567890",
            requiresPKCE = true
        )
    }

    override suspend fun search(query: String): List<SyncSearchResult>? {
        val res = app.get(
            "https://api.myanimelist.net/v2/anime?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=20&fields=id,title,main_picture,mean,media_type",
            headers = mapOf("X-MAL-CLIENT-ID" to clientId)
        ).text
        val parsed = tryParseJson<MALSearchResponse>(res) ?: return null
        return parsed.data?.mapNotNull { item ->
            val node = item.node ?: return@mapNotNull null
            SyncSearchResult(
                name = node.title ?: "",
                apiName = name,
                syncId = node.id.toString(),
                url = "$mainUrl/anime/${node.id}",
                posterUrl = node.main_picture?.large ?: node.main_picture?.medium,
                type = TvType.Anime,
                score = node.mean?.let { Score.from10(it) }
            )
        }
    }

    data class MALSearchResponse(
        val data: List<MALNodeWrapper>?
    ) {
        data class MALNodeWrapper(val node: MALNode?)
        data class MALNode(
            val id: Int,
            val title: String?,
            val main_picture: MALPicture?,
            val mean: Double?,
            val media_type: String?
        )
        data class MALPicture(val medium: String?, val large: String?)
    }
}

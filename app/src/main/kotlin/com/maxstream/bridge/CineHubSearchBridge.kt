package com.maxstream.bridge

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.searchSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class CineHubSearchResult(
    val title: String,
    val url: String,
    val posterUrl: String?,
    val providerName: String,
    val providerUrl: String,
    val mediaType: String,
    val quality: String? = null,
    val score: Int? = null
)

/**
 * CineHubSearchBridge handles concurrent querying across all registered
 * CloudStream MainAPI providers, isolating provider errors, and mapping to CineHub UI models.
 */
object CineHubSearchBridge {
    private const val TAG = "CineHubSearchBridge"
    private const val PROVIDER_TIMEOUT_MS = 15_000L

    /**
     * Executes parallel search across all active providers in APIHolder.
     */
    suspend fun searchAll(query: String): List<CineHubSearchResult> = withContext(Dispatchers.IO) {
        val providers = APIHolder.apis.toList()
        if (providers.isEmpty() || query.isBlank()) return@withContext emptyList()

        val tasks = providers.map { provider ->
            async {
                try {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        provider.searchSafe(query)
                    } ?: emptyList()
                } catch (t: Throwable) {
                    Log.w(TAG, "Search failed on provider ${provider.name}: ${t.message}")
                    emptyList()
                }
            }
        }

        val allResponses: List<SearchResponse> = tasks.awaitAll().flatten()

        allResponses.mapNotNull { response ->
            try {
                mapToCineHubSearchResult(response)
            } catch (t: Throwable) {
                null
            }
        }
    }

    /**
     * Maps a single CloudStream SearchResponse to a CineHub UI result.
     */
    fun mapToCineHubSearchResult(response: SearchResponse): CineHubSearchResult {
        val mediaType = when (response.type) {
            TvType.Movie, TvType.AnimeMovie -> "Movie"
            TvType.TvSeries, TvType.Anime, TvType.OVA, TvType.AsianDrama -> "TV"
            TvType.Live -> "Live"
            TvType.NSFW -> "NSFW"
            else -> "Media"
        }

        return CineHubSearchResult(
            title = response.name,
            url = response.url,
            posterUrl = response.posterUrl,
            providerName = response.apiName,
            providerUrl = response.url,
            mediaType = mediaType,
            quality = response.quality?.name,
            score = response.score?.toInt()
        )
    }
}

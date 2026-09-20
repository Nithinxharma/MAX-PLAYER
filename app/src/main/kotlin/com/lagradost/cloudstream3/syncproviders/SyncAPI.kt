package com.lagradost.cloudstream3.syncproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.UiText
import java.util.Date

abstract class SyncAPI : AuthAPI() {
    data class SyncSearchResult(
        val name: String,
        val apiName: String,
        val syncId: String,
        val url: String,
        val posterUrl: String? = null,
        val type: TvType? = null,
        val quality: SearchQuality? = null,
        val posterHeaders: Map<String, String>? = null,
        val id: Int? = null,
        val score: Score? = null,
        val releaseDate: Date? = null,
    )

    data class AbstractSyncStatus(
        val status: SyncWatchType,
        val score: Score? = null,
        val watchedEpisodes: Int? = null,
        val isFavorite: Boolean = false,
        val maxEpisodes: Int? = null,
    )

    data class SyncStatus(
        val status: SyncWatchType,
        val score: Score? = null,
        val watchedEpisodes: Int? = null,
        val isFavorite: Boolean = false,
        val maxEpisodes: Int? = null,
        val syncId: String,
        val id: Int? = null,
    )

    data class SyncResult(
        val title: String,
        val syncId: String,
        val url: String,
        val posterUrl: String? = null,
        val type: TvType? = null,
        val maxEpisodes: Int? = null,
        val actors: List<ActorData>? = null,
        val plot: String? = null,
        val genres: List<String>? = null,
        val totalEpisodes: Int? = null,
        val nextAiring: com.lagradost.cloudstream3.NextAiring? = null,
        val showStatus: ShowStatus? = null,
        val publicScore: Score? = null,
        val duration: Int? = null,
        val trailerUrl: String? = null,
        val id: Int? = null,
        val recommendations: List<SyncSearchResult>? = null,
    )

    data class LibraryMetadata(
        val totalEpisodes: Int? = null,
        val genres: List<String>? = null,
        val showStatus: ShowStatus? = null,
        val duration: Int? = null,
    )

    data class LibraryItem(
        val name: String,
        val url: String,
        val syncId: String,
        val episodesCompleted: Int? = null,
        val episodesTotal: Int? = null,
        val personalRating: Score? = null,
        val lastUpdatedUnixTime: Long? = null,
        val apiName: String,
        val type: TvType? = null,
        val posterUrl: String? = null,
        val posterHeaders: Map<String, String>? = null,
        val quality: SearchQuality? = null,
        val releaseDate: Date? = null,
        val id: Int? = null,
        val plot: String? = null,
        val score: Score? = null,
        val libraryMetadata: LibraryMetadata? = null,
    )

    data class LibraryList(
        val name: UiText,
        val items: List<LibraryItem>,
        val syncId: String? = null,
    )

    open val mainUrl: String = ""

    open suspend fun search(query: String): List<SyncSearchResult>? = null

    open suspend fun getStatus(id: String): AbstractSyncStatus? = null

    open suspend fun score(id: String, status: AbstractSyncStatus): Boolean = false

    open suspend fun getResult(id: String): SyncResult? = null

    open suspend fun getPersonalLibrary(): List<LibraryList>? = null

    open suspend fun sync(id: String, status: AbstractSyncStatus): Boolean = score(id, status)
}

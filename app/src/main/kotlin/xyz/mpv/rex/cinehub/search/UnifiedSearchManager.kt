package xyz.mpv.rex.cinehub.search

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.data.CineFolderMetadataManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.cinehub.stream.CloudStreamDownloadManager
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.domain.network.NetworkProtocol

enum class UnifiedSearchSource(val displayName: String) {
    STREAMING("Streaming"),
    LOCAL("Local Media"),
    SMB("SMB"),
    FTP("FTP"),
    WEBDAV("WebDAV"),
    IPTV("IPTV"),
    DOWNLOADS("Downloads"),
    HISTORY("History")
}

enum class UnifiedSearchFilter(val displayName: String) {
    ALL("All"),
    STREAMING("Streaming"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    ANIME("Anime"),
    LOCAL("Local"),
    SMB("SMB"),
    FTP("FTP"),
    WEBDAV("WebDAV"),
    IPTV("IPTV"),
    DOWNLOADS("Downloads"),
    HISTORY("History")
}

data class UnifiedSearchResult(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val source: UnifiedSearchSource,
    val type: TvType = TvType.Movie,
    val directUrlOrPath: String? = null,
    val providerId: String? = null,
    val providerName: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val extraData: Map<String, String> = emptyMap()
)

/**
 * Unified Search Engine merging all REX media sources and CloudStream Core providers.
 */
class UnifiedSearchManager(
    private val context: Context,
    private val providerRegistry: ProviderRegistry,
    private val database: MpvExDatabase,
    private val downloadManager: CloudStreamDownloadManager
) {
    private val TAG = "CineHub:UnifiedSearch"

    suspend fun search(
        query: String,
        filter: UnifiedSearchFilter = UnifiedSearchFilter.ALL
    ): List<UnifiedSearchResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val q = query.trim()
        val results = mutableListOf<UnifiedSearchResult>()

        // 1. CloudStream Streaming Providers
        val shouldSearchStreaming = filter in listOf(
            UnifiedSearchFilter.ALL,
            UnifiedSearchFilter.STREAMING,
            UnifiedSearchFilter.MOVIES,
            UnifiedSearchFilter.TV_SHOWS,
            UnifiedSearchFilter.ANIME
        )

        val streamingDeferred = if (shouldSearchStreaming) {
            async {
                val providers = providerRegistry.getEnabledProviders()
                val providerTasks = providers.map { provider ->
                    async {
                        try {
                            val items = provider.search(q)
                            items.map { item ->
                                UnifiedSearchResult(
                                    id = item.id,
                                    title = item.title,
                                    subtitle = item.providerName,
                                    posterUrl = item.posterUrl,
                                    source = UnifiedSearchSource.STREAMING,
                                    type = item.type,
                                    directUrlOrPath = item.url,
                                    providerId = item.providerId,
                                    providerName = item.providerName,
                                    year = item.year,
                                    rating = item.rating
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Provider '${provider.name}' search error: ${e.message}")
                            emptyList()
                        }
                    }
                }
                providerTasks.awaitAll().flatten()
            }
        } else null

        // 2. Local Media
        val shouldSearchLocal = filter in listOf(
            UnifiedSearchFilter.ALL,
            UnifiedSearchFilter.LOCAL,
            UnifiedSearchFilter.MOVIES,
            UnifiedSearchFilter.TV_SHOWS
        )

        val localDeferred = if (shouldSearchLocal) {
            async {
                val localItems = mutableListOf<UnifiedSearchResult>()
                runCatching {
                    val localMovies = CineFolderMetadataManager.getAllLocalMovies(context)
                    localMovies.filter { it.title.contains(q, ignoreCase = true) }.forEach { m ->
                        localItems.add(
                            UnifiedSearchResult(
                                id = m.videoFilePath,
                                title = m.title,
                                subtitle = "Local Movie (${m.premiered})",
                                posterUrl = m.posterPath,
                                source = UnifiedSearchSource.LOCAL,
                                type = TvType.Movie,
                                directUrlOrPath = m.videoFilePath,
                                year = m.premiered.take(4).toIntOrNull()
                            )
                        )
                    }

                    val localTv = CineFolderMetadataManager.getAllLocalTvShows(context)
                    localTv.filter { it.title.contains(q, ignoreCase = true) }.forEach { tv ->
                        localItems.add(
                            UnifiedSearchResult(
                                id = tv.folderPath,
                                title = tv.title,
                                subtitle = "Local TV Series",
                                posterUrl = tv.posterPath,
                                source = UnifiedSearchSource.LOCAL,
                                type = TvType.TvSeries,
                                directUrlOrPath = tv.folderPath
                            )
                        )
                    }
                }
                localItems
            }
        } else null

        // 3. Network Storage (SMB, FTP, WebDAV)
        val shouldSearchNetwork = filter in listOf(
            UnifiedSearchFilter.ALL,
            UnifiedSearchFilter.SMB,
            UnifiedSearchFilter.FTP,
            UnifiedSearchFilter.WEBDAV
        )

        val networkDeferred = if (shouldSearchNetwork) {
            async {
                val netItems = mutableListOf<UnifiedSearchResult>()
                runCatching {
                    val connections = database.networkConnectionDao().getAllConnectionsList()
                    connections.forEach { conn ->
                        val matchesQuery = conn.name.contains(q, ignoreCase = true) || conn.host.contains(q, ignoreCase = true)
                        val source = when (conn.protocol) {
                            NetworkProtocol.SMB -> UnifiedSearchSource.SMB
                            NetworkProtocol.FTP -> UnifiedSearchSource.FTP
                            NetworkProtocol.WEBDAV -> UnifiedSearchSource.WEBDAV
                            else -> UnifiedSearchSource.SMB
                        }

                        val matchesFilter = when (filter) {
                            UnifiedSearchFilter.SMB -> conn.protocol == NetworkProtocol.SMB
                            UnifiedSearchFilter.FTP -> conn.protocol == NetworkProtocol.FTP
                            UnifiedSearchFilter.WEBDAV -> conn.protocol == NetworkProtocol.WEBDAV
                            else -> true
                        }

                        if (matchesQuery && matchesFilter) {
                            netItems.add(
                                UnifiedSearchResult(
                                    id = "network_${conn.id}",
                                    title = conn.name,
                                    subtitle = "${conn.protocol.name} - ${conn.host}",
                                    source = source,
                                    type = TvType.Movie,
                                    directUrlOrPath = conn.path
                                )
                            )
                        }
                    }
                }
                netItems
            }
        } else null

        // 4. IPTV Playlists & Channels
        val shouldSearchIptv = filter in listOf(UnifiedSearchFilter.ALL, UnifiedSearchFilter.IPTV)
        val iptvDeferred = if (shouldSearchIptv) {
            async {
                val iptvItems = mutableListOf<UnifiedSearchResult>()
                runCatching {
                    val playlists = database.playlistDao().getAllPlaylists()
                    playlists.filter { it.name.contains(q, ignoreCase = true) }.forEach { pl ->
                        iptvItems.add(
                            UnifiedSearchResult(
                                id = "playlist_${pl.id}",
                                title = pl.name,
                                subtitle = "IPTV / M3U Playlist",
                                source = UnifiedSearchSource.IPTV,
                                type = TvType.Live
                            )
                        )
                    }
                }
                iptvItems
            }
        } else null

        // 5. Downloads
        val shouldSearchDownloads = filter in listOf(UnifiedSearchFilter.ALL, UnifiedSearchFilter.DOWNLOADS)
        val downloadsDeferred = if (shouldSearchDownloads) {
            async {
                val dlItems = mutableListOf<UnifiedSearchResult>()
                downloadManager.downloads.value.filter {
                    it.title.contains(q, ignoreCase = true) || (it.episodeTitle?.contains(q, ignoreCase = true) == true)
                }.forEach { task ->
                    dlItems.add(
                        UnifiedSearchResult(
                            id = task.id,
                            title = task.getDisplayName(),
                            subtitle = "Downloaded (${task.status.name})",
                            posterUrl = task.posterUrl,
                            source = UnifiedSearchSource.DOWNLOADS,
                            type = if (task.season != null) TvType.TvSeries else TvType.Movie,
                            directUrlOrPath = task.localPath
                        )
                    )
                }
                dlItems
            }
        } else null

        // 6. History / Recently Played
        val shouldSearchHistory = filter in listOf(UnifiedSearchFilter.ALL, UnifiedSearchFilter.HISTORY)
        val historyDeferred = if (shouldSearchHistory) {
            async {
                val historyItems = mutableListOf<UnifiedSearchResult>()
                runCatching {
                    val recents = database.recentlyPlayedDao().getRecentlyPlayed(50)
                    recents.filter {
                        it.fileName.contains(q, ignoreCase = true) || (it.videoTitle?.contains(q, ignoreCase = true) == true)
                    }.forEach { entity ->
                        val displayTitle = entity.videoTitle ?: entity.fileName
                        historyItems.add(
                            UnifiedSearchResult(
                                id = "history_${entity.id}",
                                title = displayTitle,
                                subtitle = "Recently Watched",
                                source = UnifiedSearchSource.HISTORY,
                                type = TvType.Movie,
                                directUrlOrPath = entity.filePath
                            )
                        )
                    }
                }
                historyItems
            }
        } else null

        // Await and collect all active searches
        streamingDeferred?.await()?.let { results.addAll(it) }
        localDeferred?.await()?.let { results.addAll(it) }
        networkDeferred?.await()?.let { results.addAll(it) }
        iptvDeferred?.await()?.let { results.addAll(it) }
        downloadsDeferred?.await()?.let { results.addAll(it) }
        historyDeferred?.await()?.let { results.addAll(it) }

        // Apply secondary type filtering if specific type selected
        val filtered = when (filter) {
            UnifiedSearchFilter.MOVIES -> results.filter { it.type == TvType.Movie }
            UnifiedSearchFilter.TV_SHOWS -> results.filter { it.type == TvType.TvSeries }
            UnifiedSearchFilter.ANIME -> results.filter { it.type == TvType.Anime }
            UnifiedSearchFilter.STREAMING -> results.filter { it.source == UnifiedSearchSource.STREAMING }
            UnifiedSearchFilter.LOCAL -> results.filter { it.source == UnifiedSearchSource.LOCAL }
            UnifiedSearchFilter.SMB -> results.filter { it.source == UnifiedSearchSource.SMB }
            UnifiedSearchFilter.FTP -> results.filter { it.source == UnifiedSearchSource.FTP }
            UnifiedSearchFilter.WEBDAV -> results.filter { it.source == UnifiedSearchSource.WEBDAV }
            UnifiedSearchFilter.IPTV -> results.filter { it.source == UnifiedSearchSource.IPTV }
            UnifiedSearchFilter.DOWNLOADS -> results.filter { it.source == UnifiedSearchSource.DOWNLOADS }
            UnifiedSearchFilter.HISTORY -> results.filter { it.source == UnifiedSearchSource.HISTORY }
            UnifiedSearchFilter.ALL -> results
        }

        // Deduplicate items by title and ID
        filtered.distinctBy { "${it.source}_${it.id}_${it.title}" }
    }
}

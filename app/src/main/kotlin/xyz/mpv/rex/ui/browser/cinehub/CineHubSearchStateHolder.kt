package xyz.mpv.rex.ui.browser.cinehub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.SearchResponse
import xyz.mpv.rex.cinehub.data.TMDBMovieNode
import xyz.mpv.rex.cinehub.data.TMDBTvNode
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem

/**
 * Global Search State Holder for Max Stream UI 2.0.
 * Preserves search state, active query, filters, scroll position,
 * and results across full-screen movie detail navigation and back events.
 */
object CineHubSearchStateHolder {
    var searchQuery by mutableStateOf("")
    var isSearchActive by mutableStateOf(false)
    var selectedFilterCategory by mutableStateOf("All") // "All", "Movies", "TV Shows", "Anime", "Providers"
    var selectedProviderFilter by mutableStateOf<String?>(null)
    
    var isSearching by mutableStateOf(false)
    var hasSearched by mutableStateOf(false)
    
    // Result collections
    val tmdbMovieResults = mutableStateListOf<TMDBMovieNode>()
    val tmdbTvResults = mutableStateListOf<TMDBTvNode>()
    val providerResults = mutableStateListOf<CineHubSearchItem>()
    val rawCloudStreamResults = mutableStateListOf<SearchResponse>()
    
    // Recent searches with persistent in-memory tracking
    val recentSearches = mutableStateListOf(
        "Breaking Bad",
        "Interstellar",
        "The Dark Knight",
        "Stranger Things"
    )

    // OTT Curated Discovery Suggestions
    val trendingSearches = listOf(
        "Dune: Part Two",
        "Deadpool & Wolverine",
        "Shogun",
        "House of the Dragon",
        "The Bear",
        "Fallout",
        "Severance"
    )

    val popularMovies = listOf(
        "Interstellar",
        "Inception",
        "The Dark Knight",
        "Oppenheimer",
        "Gladiator II",
        "Avatar: The Way of Water"
    )

    val popularShows = listOf(
        "Breaking Bad",
        "Stranger Things",
        "The Last of Us",
        "Game of Thrones",
        "Succession",
        "Loki"
    )

    val quickFilterCategories = listOf(
        "All",
        "Movies",
        "TV Shows",
        "Anime",
        "Trending",
        "4K UHD",
        "Top Rated"
    )

    // Scroll state preservation across detail navigation
    var scrollIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            recentSearches.remove(trimmed)
            recentSearches.add(0, trimmed)
            if (recentSearches.size > 12) {
                recentSearches.removeAt(recentSearches.lastIndex)
            }
        }
    }

    fun removeRecentSearch(query: String) {
        recentSearches.remove(query)
    }

    fun clearAllResults() {
        tmdbMovieResults.clear()
        tmdbTvResults.clear()
        providerResults.clear()
        rawCloudStreamResults.clear()
        hasSearched = false
    }

    fun reset() {
        searchQuery = ""
        isSearchActive = false
        selectedFilterCategory = "All"
        selectedProviderFilter = null
        isSearching = false
        clearAllResults()
    }
}

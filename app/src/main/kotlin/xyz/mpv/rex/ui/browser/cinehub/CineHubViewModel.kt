package xyz.mpv.rex.ui.browser.cinehub

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CineHubViewModel manages:
 * 1. Concurrent multi-provider searching across registered Cloudstream APIs.
 * 2. Retrieving full media details and episode listings.
 * 3. Extracting and prioritizing stream links (and subtitles) for REX-Player playback.
 */
class CineHubViewModel : ViewModel() {

    companion object {
        private const val TAG = "CineHubViewModel"
    }

    // --- Search State ---
    private val _searchResults = MutableStateFlow<List<SearchResponse>>(emptyList())
    val searchResults: StateFlow<List<SearchResponse>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // --- Details & Episode State ---
    private val _selectedMediaDetails = MutableStateFlow<LoadResponse?>(null)
    val selectedMediaDetails: StateFlow<LoadResponse?> = _selectedMediaDetails.asStateFlow()

    private val _isLoadingDetails = MutableStateFlow(false)
    val isLoadingDetails: StateFlow<Boolean> = _isLoadingDetails.asStateFlow()

    // --- Extracted Streams State ---
    private val _extractedStreams = MutableStateFlow<List<ExtractorLink>>(emptyList())
    val extractedStreams: StateFlow<List<ExtractorLink>> = _extractedStreams.asStateFlow()

    private val _extractedSubtitles = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val extractedSubtitles: StateFlow<List<SubtitleFile>> = _extractedSubtitles.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /**
     * 1. Search Function:
     * Iterates through APIHolder.apis, executes provider.search(query) concurrently
     * using async(Dispatchers.IO), gracefully handles errors per provider, merges the results,
     * and updates searchResults.
     */
    fun searchContent(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _statusMessage.value = null

            val apis = APIHolder.apis.toList()
            if (apis.isEmpty()) {
                Log.w(TAG, "No Cloudstream providers loaded in APIHolder")
                _searchResults.value = emptyList()
                _isSearching.value = false
                _statusMessage.value = "No extension providers installed or active"
                return@launch
            }

            val aggregatedResults = withContext(Dispatchers.IO) {
                val deferredList = apis.map { provider ->
                    async {
                        try {
                            provider.search(trimmed)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Search error in provider '${provider.name}': ${t.message}")
                            emptyList()
                        }
                    }
                }
                deferredList.awaitAll().flatten()
            }

            _searchResults.value = aggregatedResults
            _isSearching.value = false
            if (aggregatedResults.isEmpty()) {
                _statusMessage.value = "No results found for \"$trimmed\""
            }
        }
    }

    /**
     * 2. Load Details Function:
     * Fetches the specific provider by name, calls provider.load(url), and
     * updates selectedMediaDetails.
     */
    fun loadMediaDetails(providerName: String, url: String) {
        viewModelScope.launch {
            _isLoadingDetails.value = true
            _selectedMediaDetails.value = null
            _extractedStreams.value = emptyList()
            _extractedSubtitles.value = emptyList()

            val provider = findProvider(providerName)
            if (provider == null) {
                Log.e(TAG, "Provider not found: $providerName")
                _isLoadingDetails.value = false
                _statusMessage.value = "Provider '$providerName' not found"
                return@launch
            }

            val details = withContext(Dispatchers.IO) {
                try {
                    provider.load(url)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error loading details from '$providerName' for $url: ${t.message}", t)
                    null
                }
            }

            _selectedMediaDetails.value = details
            _isLoadingDetails.value = false
        }
    }

    /**
     * 3. Extract Streams Function:
     * Calls provider.loadLinks(episodeData) and collects all ExtractorLinks
     * and SubtitleFiles emitted by the callbacks.
     */
    fun getStreamLinks(
        providerName: String,
        episodeData: String,
        onLinksReady: ((List<ExtractorLink>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isExtracting.value = true
            _extractedStreams.value = emptyList()
            _extractedSubtitles.value = emptyList()

            val provider = findProvider(providerName)
            if (provider == null) {
                Log.e(TAG, "Provider not found for link extraction: $providerName")
                _isExtracting.value = false
                _statusMessage.value = "Provider '$providerName' not found"
                return@launch
            }

            val links = mutableListOf<ExtractorLink>()
            val subtitles = mutableListOf<SubtitleFile>()

            withContext(Dispatchers.IO) {
                try {
                    provider.loadLinks(
                        data = episodeData,
                        isCasting = false,
                        subtitleCallback = { sub ->
                            synchronized(subtitles) {
                                subtitles.add(sub)
                            }
                        },
                        callback = { link ->
                            synchronized(links) {
                                links.add(link)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "Link extraction failed on '$providerName': ${t.message}", t)
                }
            }

            // Sort links with highest quality first
            val sortedLinks = links.sortedByDescending { it.quality }
            _extractedStreams.value = sortedLinks
            _extractedSubtitles.value = subtitles
            _isExtracting.value = false

            onLinksReady?.invoke(sortedLinks)
        }
    }

    /**
     * Clears current detail selection and active stream extraction state.
     */
    fun clearSelection() {
        _selectedMediaDetails.value = null
        _extractedStreams.value = emptyList()
        _extractedSubtitles.value = emptyList()
        _isExtracting.value = false
        _isLoadingDetails.value = false
    }

    /**
     * Helper to find a provider by apiName or name.
     */
    private fun findProvider(providerName: String): MainAPI? {
        val apis = APIHolder.apis.toList()
        return apis.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
            ?: apis.firstOrNull { it.name.contains(providerName, ignoreCase = true) }
            ?: APIHolder.getApi(providerName)
    }
}

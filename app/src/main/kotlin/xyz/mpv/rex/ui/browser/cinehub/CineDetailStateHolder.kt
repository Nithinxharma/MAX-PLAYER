package xyz.mpv.rex.ui.browser.cinehub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import xyz.mpv.rex.presentation.Screen

/**
 * Manages state and transitions for the full-screen CineDetailScreen.
 */
object CineDetailStateHolder {
    var activeDetailItem by mutableStateOf<Any?>(null)
    
    // For pending quality selection on multi-links
    var pendingStreamTitle by mutableStateOf("")
    var pendingStreamLinks by mutableStateOf<List<ExtractorLink>>(emptyList())
    var pendingSubtitles by mutableStateOf<List<SubtitleFile>>(emptyList())
    var pendingEpisodeMetadataJson by mutableStateOf<String?>(null)
    var pendingPosterUrl by mutableStateOf<String?>(null)
    var pendingOverview by mutableStateOf<String?>(null)
    var pendingYear by mutableStateOf<String?>(null)
    var pendingRating by mutableStateOf<Double?>(null)
    var pendingProviderName by mutableStateOf<String?>(null)

    fun open(backstack: NavBackStack<Screen>, item: Any) {
        activeDetailItem = item
        backstack.add(CineDetailScreen)
    }

    fun clear() {
        activeDetailItem = null
        pendingStreamTitle = ""
        pendingStreamLinks = emptyList()
        pendingSubtitles = emptyList()
        pendingEpisodeMetadataJson = null
        pendingPosterUrl = null
        pendingOverview = null
        pendingYear = null
        pendingRating = null
        pendingProviderName = null
    }
}

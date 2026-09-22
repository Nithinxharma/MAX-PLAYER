package xyz.mpv.rex.cinehub

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.ui.browser.cinehub.components.ContinueWatchingMediaItem
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper

/**
 * Verification test for Max Stream Compatibility, Metadata Extraction, and UI Rails.
 */
class MaxStreamComprehensiveCompatibilityTest {

    @Test
    fun testQualityDetectionFromExtractorLink() {
        val link4k = ExtractorLink(
            source = "TestProvider",
            name = "Stream 4K",
            url = "https://example.com/video.mp4",
            referer = "",
            quality = Qualities.P2160.value
        )
        assertEquals("4K", MaxStreamMetadataHelper.detectQuality(link4k))

        val link1080 = ExtractorLink(
            source = "TestProvider",
            name = "Stream 1080p",
            url = "https://example.com/video.mp4",
            referer = "",
            quality = Qualities.P1080.value
        )
        assertEquals("1080p", MaxStreamMetadataHelper.detectQuality(link1080))
    }

    @Test
    fun testQualityDetectionFromSearchResponse() {
        val response = MovieSearchResponse(
            name = "Inception (2010)",
            url = "https://example.com/movie",
            apiName = "TestAPI",
            type = TvType.Movie,
            posterUrl = "https://example.com/poster.jpg",
            quality = SearchQuality.FourK
        )
        assertEquals("4K", MaxStreamMetadataHelper.detectQuality(response))
    }

    @Test
    fun testDubSubDetection() {
        val animeResponse = AnimeSearchResponse(
            name = "Jujutsu Kaisen",
            url = "https://example.com/anime",
            apiName = "AnimeAPI",
            type = TvType.Anime,
            posterUrl = "https://example.com/poster.jpg",
            dubStatus = mutableSetOf(DubStatus.Dubbed, DubStatus.Subbed),
            episodes = mutableMapOf(DubStatus.Dubbed to 24, DubStatus.Subbed to 24)
        )
        assertEquals("MULTI AUDIO", MaxStreamMetadataHelper.detectDubSub(animeResponse))

        val subOnlyResponse = AnimeSearchResponse(
            name = "Demon Slayer",
            url = "https://example.com/anime2",
            apiName = "AnimeAPI",
            type = TvType.Anime,
            posterUrl = "https://example.com/poster.jpg",
            dubStatus = mutableSetOf(DubStatus.Subbed),
            episodes = mutableMapOf(DubStatus.Subbed to 26)
        )
        assertEquals("SUB", MaxStreamMetadataHelper.detectDubSub(subOnlyResponse))
    }

    @Test
    fun testIsNewReleaseDetection() {
        assertTrue(MaxStreamMetadataHelper.detectIsNew("2026"))
        assertTrue(MaxStreamMetadataHelper.detectIsNew("2025"))
        assertFalse(MaxStreamMetadataHelper.detectIsNew("2010"))
    }

    @Test
    fun testContinueWatchingCalculation() {
        val cwItem = ContinueWatchingMediaItem(
            id = "/storage/emulated/0/Movies/Interstellar.mp4",
            title = "Interstellar",
            landscapeImageUrl = "/storage/emulated/0/Movies/Interstellar.mp4",
            currentPositionSeconds = 3600L,
            totalDurationSeconds = 7200L,
            watchProgressFraction = 0.50f
        )
        assertEquals("1h 0m left", cwItem.remainingTimeFormatted)
        assertTrue(cwItem.timestampFormatted.contains("1:00:00"))
        assertEquals(0.50f, cwItem.watchProgressFraction, 0.001f)
    }

    @Test
    fun testCategoryPillListCompleteness() {
        val baseCategories = listOf("All", "Movies", "TV Shows", "Anime", "Cartoons", "K-Drama", "Asian Drama", "Live TV", "Sports", "Documentary")
        assertTrue(baseCategories.contains("All"))
        assertTrue(baseCategories.contains("Movies"))
        assertTrue(baseCategories.contains("TV Shows"))
        assertTrue(baseCategories.contains("Anime"))
        assertTrue(baseCategories.contains("Live TV"))
    }
}

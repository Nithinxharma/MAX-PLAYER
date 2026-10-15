package xyz.mpv.rex.core.ui

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import xyz.mpv.rex.domain.media.model.Video
import java.util.Calendar

/**
 * Metadata analysis and quality/audio detection helper for Max Stream.
 * Centralized in xyz.mpv.rex.core.ui for shared usage across player, browser, and streaming screens.
 */
object MaxStreamMetadataHelper {

    /**
     * Determines highest available media quality based on real detected properties:
     * - ExtractorLink / Resolved stream resolution
     * - SearchQuality / Provider metadata
     * - Media dimensions (Local / Indexed Video)
     * - Title/filename tags (e.g. 2160p, 4K, 1080p, 720p, 480p)
     */
    fun detectQuality(item: Any?): String? {
        if (item == null) return null

        return when (item) {
            is ExtractorLink -> {
                when (item.quality) {
                    Qualities.P2160.value, 2160 -> "4K"
                    Qualities.P1440.value, 1440 -> "2K"
                    Qualities.P1080.value, 1080 -> "1080p"
                    Qualities.P720.value, 720 -> "720p"
                    Qualities.P480.value, 480 -> "480p"
                    Qualities.P360.value, 360 -> "360p"
                    Qualities.P240.value, 240 -> "240p"
                    else -> extractQualityFromText(item.name) ?: "HD"
                }
            }
            is SearchResponse -> {
                val qualityEnum = item.quality
                if (qualityEnum != null) {
                    when (qualityEnum) {
                        SearchQuality.FourK, SearchQuality.UHD -> "4K"
                        SearchQuality.HDR -> "HDR"
                        SearchQuality.BlueRay, SearchQuality.WebRip, SearchQuality.HD -> "1080p"
                        SearchQuality.HQ -> "720p"
                        SearchQuality.SD, SearchQuality.DVD -> "480p"
                        SearchQuality.Cam, SearchQuality.HdCam, SearchQuality.Telesync -> "CAM"
                        else -> extractQualityFromText(item.name)
                    }
                } else {
                    extractQualityFromText(item.name)
                }
            }
            is Video -> {
                val w = item.width ?: 0
                val h = item.height ?: 0
                when {
                    w >= 3800 || h >= 2100 -> "4K"
                    w >= 2500 || h >= 1400 -> "2K"
                    w >= 1900 || h >= 1000 -> "1080p"
                    w >= 1200 || h >= 700 -> "720p"
                    w >= 800 || h >= 400 -> "480p"
                    else -> extractQualityFromText(item.displayName) ?: extractQualityFromText(item.path)
                }
            }
            is String -> {
                extractQualityFromText(item)
            }
            else -> null
        }
    }

    /**
     * Determines whether media contains Dubbed, Subbed, or Multi-Audio streams.
     */
    fun detectDubSub(item: Any?): String? {
        if (item == null) return null

        return when (item) {
            is AnimeSearchResponse -> {
                val dub = item.dubStatus
                when {
                    dub?.contains(DubStatus.Dubbed) == true && dub.contains(DubStatus.Subbed) -> "MULTI AUDIO"
                    dub?.contains(DubStatus.Dubbed) == true -> "DUB"
                    dub?.contains(DubStatus.Subbed) == true -> "SUB"
                    else -> extractDubSubFromText(item.name)
                }
            }
            is SearchResponse -> {
                extractDubSubFromText(item.name)
            }
            is Video -> {
                extractDubSubFromText(item.displayName) ?: extractDubSubFromText(item.path)
            }
            is String -> {
                extractDubSubFromText(item)
            }
            else -> null
        }
    }

    /**
     * Determines highest available media quality with "HD" fallback if unknown.
     */
    fun detectQualityOrDefault(item: Any?): String {
        return detectQuality(item) ?: "HD"
    }

    /**
     * Converts a TMDB image path or URL to the highest resolution fanart/backdrop available.
     * Always selects original/w1280 landscape backdrop where applicable.
     */
    fun toHighResFanart(imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return null
        if (imageUrl.contains("image.tmdb.org")) {
            return imageUrl.replace(Regex("""/w\d{3,4}/"""), "/original/")
        }
        return imageUrl
    }

    fun detectIsNew(yearStr: String?): Boolean {
        if (yearStr.isNullOrBlank()) return false
        val yearNum = Regex("""\b(19\d{2}|20\d{2})\b""").find(yearStr)?.value?.toIntOrNull() ?: return false
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return yearNum >= (currentYear - 1)
    }

    private fun extractQualityFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        return when {
            lower.contains("4k") || lower.contains("2160p") || lower.contains("uhd") -> "4K"
            lower.contains("1440p") || lower.contains("2k") || lower.contains("qhd") -> "2K"
            lower.contains("1080p") || lower.contains("fhd") || lower.contains("bluray") || lower.contains("bdrip") || lower.contains("web-dl") -> "1080p"
            lower.contains("720p") || lower.contains("hdrip") -> "720p"
            lower.contains("480p") || lower.contains("dvdrip") || lower.contains("sd") -> "480p"
            lower.contains("cam") || lower.contains("telesync") || lower.contains("hdcam") -> "CAM"
            else -> null
        }
    }

    private fun extractDubSubFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        return when {
            lower.contains("multi-audio") || lower.contains("multi audio") || lower.contains("dual audio") || lower.contains("dual-audio") || lower.contains("multi") -> "MULTI AUDIO"
            lower.contains("[dub]") || lower.contains("(dub)") || lower.contains(" dub ") || lower.endsWith(" dub") || lower.contains("dubbed") -> "DUB"
            lower.contains("[sub]") || lower.contains("(sub)") || lower.contains(" sub ") || lower.endsWith(" sub") || lower.contains("subbed") || lower.contains("softsub") -> "SUB"
            lower.contains("[raw]") || lower.contains("(raw)") || lower.contains(" raw ") -> "RAW"
            else -> null
        }
    }
}

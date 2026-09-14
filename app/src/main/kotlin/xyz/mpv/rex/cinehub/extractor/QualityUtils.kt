package xyz.mpv.rex.cinehub.extractor

/**
 * Utility functions for video stream quality parsing, ranking, and formatting.
 */
object QualityUtils {

    fun parseQuality(qualityString: String?): Int {
        if (qualityString.isNullOrBlank()) return 1080
        val cleaned = qualityString.lowercase().replace("p", "").trim()
        return when (cleaned) {
            "4k", "2160" -> 2160
            "2k", "1440" -> 1440
            "1080", "fhd" -> 1080
            "720", "hd" -> 720
            "480", "sd" -> 480
            "360" -> 360
            "240" -> 240
            "auto" -> 0
            else -> cleaned.toIntOrNull() ?: 1080
        }
    }

    fun formatQuality(qualityInt: Int): String {
        return when (qualityInt) {
            2160 -> "4K"
            1440 -> "1440p"
            1080 -> "1080p"
            720 -> "720p"
            480 -> "480p"
            360 -> "360p"
            240 -> "240p"
            0 -> "Auto"
            else -> if (qualityInt > 0) "${qualityInt}p" else "Auto"
        }
    }
}

package xyz.mpv.rex.cinehub.utils

import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.Locale

/**
 * Formats stream links strictly into "Quality-language" format (e.g., "1080p-Dual Audio", "720p-Hindi", "1080p-English").
 * Completely removes raw filenames, release hashes, domains, and file extensions.
 */
object StreamLinkFormatter {

    private val QUALITY_REGEX = Regex("(?i)\\b(2160p|4k|uhd|1080p|fhd|720p|hd|480p|sd|360p)\\b")
    private val EXTENSION_REGEX = Regex("(?i)\\.(mkv|mp4|avi|ts|m3u8|mpd|webm|flv|mov|wmv)$")
    private val URL_PROTOCOL_REGEX = Regex("(?i)https?://[^/]+")

    // Common audio / language markers found in release titles
    private val LANGUAGE_PATTERNS = listOf(
        Regex("(?i)\\b(dual[\\s._-]?audio)\\b") to "Dual Audio",
        Regex("(?i)\\b(multi[\\s._-]?audio|multi)\\b") to "Multi Audio",
        Regex("(?i)\\b(hindi|hin)\\b") to "Hindi",
        Regex("(?i)\\b(english|eng)\\b") to "English",
        Regex("(?i)\\b(tamil|tam)\\b") to "Tamil",
        Regex("(?i)\\b(telugu|tel)\\b") to "Telugu",
        Regex("(?i)\\b(malayalam|mal)\\b") to "Malayalam",
        Regex("(?i)\\b(kannada|kan)\\b") to "Kannada",
        Regex("(?i)\\b(bengali|ben)\\b") to "Bengali",
        Regex("(?i)\\b(japanese|jap|jpn)\\b") to "Japanese",
        Regex("(?i)\\b(korean|kor)\\b") to "Korean",
        Regex("(?i)\\b(chinese|chi)\\b") to "Chinese",
        Regex("(?i)\\b(spanish|spa|esp)\\b") to "Spanish",
        Regex("(?i)\\b(french|fre|fra)\\b") to "French",
        Regex("(?i)\\b(german|ger|deu)\\b") to "German",
        Regex("(?i)\\b(italian|ita)\\b") to "Italian",
        Regex("(?i)\\b(portuguese|por)\\b") to "Portuguese",
        Regex("(?i)\\b(russian|rus)\\b") to "Russian",
        Regex("(?i)\\b(arabic|ara)\\b") to "Arabic",
        Regex("(?i)\\b(subbed|sub)\\b") to "Sub",
        Regex("(?i)\\b(dubbed|dub)\\b") to "Dub",
        Regex("(?i)\\b(original|org)\\b") to "Original"
    )

    /**
     * Formats an ExtractorLink into "Quality-language" string.
     * Example: "1080p-Dual Audio", "720p-English", "1080p-Hindi"
     */
    fun formatQualityLanguage(link: ExtractorLink, fallbackProvider: String = ""): String {
        return formatQualityLanguage(
            quality = link.quality,
            rawName = link.name,
            source = link.source,
            url = link.url,
            fallbackProvider = fallbackProvider
        )
    }

    /**
     * Formats raw inputs into "Quality-language" string.
     */
    fun formatQualityLanguage(
        quality: Int,
        rawName: String?,
        source: String? = null,
        url: String? = null,
        fallbackProvider: String = ""
    ): String {
        // 1. Determine Quality string
        var qualityStr = when {
            quality >= 2160 -> "4K"
            quality >= 1080 -> "1080p"
            quality >= 720 -> "720p"
            quality >= 480 -> "480p"
            quality >= 360 -> "360p"
            quality > 0 -> "${quality}p"
            else -> null
        }

        val textToInspect = listOfNotNull(rawName, source, url)
            .joinToString(" ")
            .replace(URL_PROTOCOL_REGEX, "")
            .replace(EXTENSION_REGEX, "")

        if (qualityStr == null) {
            val qMatch = QUALITY_REGEX.find(textToInspect)
            if (qMatch != null) {
                val found = qMatch.groupValues[1].uppercase(Locale.ROOT)
                qualityStr = when (found) {
                    "2160P", "4K", "UHD" -> "4K"
                    "1080P", "FHD" -> "1080p"
                    "720P", "HD" -> "720p"
                    "480P", "SD" -> "480p"
                    "360P" -> "360p"
                    else -> found.lowercase(Locale.ROOT)
                }
            }
        }

        val finalQuality = qualityStr ?: "1080p"

        // 2. Determine Language string
        val detectedLanguages = mutableListOf<String>()
        for ((regex, normalized) in LANGUAGE_PATTERNS) {
            if (regex.containsMatchIn(textToInspect)) {
                if (!detectedLanguages.contains(normalized)) {
                    detectedLanguages.add(normalized)
                }
            }
        }

        val languageStr = when {
            detectedLanguages.contains("Dual Audio") -> "Dual Audio"
            detectedLanguages.contains("Multi Audio") -> "Multi Audio"
            detectedLanguages.isNotEmpty() -> detectedLanguages.take(2).joinToString(" & ")
            source != null && source.isNotBlank() && !source.contains("http") && !source.contains(".") -> {
                // If source has a clean label like "English" or "Hindi"
                source.trim()
            }
            fallbackProvider.isNotBlank() && !fallbackProvider.contains("http") -> {
                // Check if fallbackProvider has language indication
                for ((regex, normalized) in LANGUAGE_PATTERNS) {
                    if (regex.containsMatchIn(fallbackProvider)) {
                        return "$finalQuality-$normalized"
                    }
                }
                "Original"
            }
            else -> "Original"
        }

        return "$finalQuality-$languageStr"
    }

    /**
     * Cleans an existing label if it already looks somewhat like "1080p - Something.mkv"
     * and forces it to strictly follow "Quality-language".
     */
    fun cleanExistingLabel(label: String, fallbackProvider: String = ""): String {
        if (label.isBlank()) return "1080p-Original"
        
        // If already in strict "Quality-Language" format (e.g. "1080p-English" or "720p-Dual Audio")
        if (Regex("^(4K|\\d{3,4}p)-[A-Za-z0-9 &]+$").matches(label.trim())) {
            return label.trim()
        }

        return formatQualityLanguage(
            quality = 0,
            rawName = label,
            source = null,
            url = null,
            fallbackProvider = fallbackProvider
        )
    }
}

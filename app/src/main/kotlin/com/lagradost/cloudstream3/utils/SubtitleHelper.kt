package com.lagradost.cloudstream3.utils

import java.util.Locale

object SubtitleHelper {
    data class SubtitleLanguage(
        val name: String,
        val ISO_639_1: String,
        val ISO_639_2: String? = null,
        val nativeName: String? = null
    )

    val languages: List<SubtitleLanguage> = listOf(
        SubtitleLanguage("English", "en", "eng"),
        SubtitleLanguage("Spanish", "es", "spa"),
        SubtitleLanguage("French", "fr", "fra"),
        SubtitleLanguage("German", "de", "deu"),
        SubtitleLanguage("Italian", "it", "ita"),
        SubtitleLanguage("Portuguese", "pt", "por"),
        SubtitleLanguage("Russian", "ru", "rus"),
        SubtitleLanguage("Japanese", "ja", "jpn"),
        SubtitleLanguage("Korean", "ko", "kor"),
        SubtitleLanguage("Chinese", "zh", "zho"),
        SubtitleLanguage("Arabic", "ar", "ara"),
        SubtitleLanguage("Hindi", "hi", "hin"),
        SubtitleLanguage("Indonesian", "id", "ind"),
        SubtitleLanguage("Vietnamese", "vi", "vie"),
        SubtitleLanguage("Turkish", "tr", "tur"),
        SubtitleLanguage("Thai", "th", "tha")
    )

    fun fromTwoLettersToLanguage(twoLetters: String?): String? {
        if (twoLetters.isNullOrBlank()) return null
        val clean = twoLetters.trim().lowercase().substringBefore("-").substringBefore("_")
        val match = languages.firstOrNull { it.ISO_639_1.equals(clean, ignoreCase = true) || it.ISO_639_2?.equals(clean, ignoreCase = true) == true }
        if (match != null) return match.name
        return try {
            val loc = Locale.forLanguageTag(clean)
            if (loc.displayLanguage.isNotBlank()) loc.displayLanguage else clean
        } catch (_: Throwable) {
            clean
        }
    }

    fun fromLanguageToTwoLetters(language: String?): String? {
        if (language.isNullOrBlank()) return null
        val match = languages.firstOrNull { it.name.equals(language, ignoreCase = true) }
        return match?.ISO_639_1 ?: language
    }
}

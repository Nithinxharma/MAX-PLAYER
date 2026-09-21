package xyz.mpv.rex.cinehub.extension.util

import java.util.Locale

object LanguageUtils {
    fun formatLanguage(codeOrName: String?): String {
        if (codeOrName.isNullOrBlank()) return "English"
        val clean = codeOrName.trim()
        val lower = clean.lowercase(Locale.ROOT)
        return when (lower) {
            "en", "eng" -> "English"
            "hi", "hin" -> "Hindi"
            "es", "spa" -> "Spanish"
            "pt", "por", "pt-br", "pt_br" -> "Portuguese"
            "fr", "fra" -> "French"
            "de", "deu", "ger" -> "German"
            "it", "ita" -> "Italian"
            "ru", "rus" -> "Russian"
            "ar", "ara" -> "Arabic"
            "ja", "jpn" -> "Japanese"
            "ko", "kor" -> "Korean"
            "zh", "zho", "chi", "zh-cn", "zh-tw" -> "Chinese"
            "id", "ind" -> "Indonesian"
            "vi", "vie" -> "Vietnamese"
            "th", "tha" -> "Thai"
            "tr", "tur" -> "Turkish"
            "ta", "tam" -> "Tamil"
            "te", "tel" -> "Telugu"
            "ml", "mal" -> "Malayalam"
            "kn", "kan" -> "Kannada"
            "bn", "ben" -> "Bengali"
            "mr", "mar" -> "Marathi"
            "pa", "pan" -> "Punjabi"
            "ur", "urd" -> "Urdu"
            "tl", "tgl", "fil" -> "Filipino"
            "pl", "pol" -> "Polish"
            "nl", "nld", "dut" -> "Dutch"
            "ro", "ron", "rum" -> "Romanian"
            "all", "multi", "none", "universal" -> "Multi / All"
            else -> clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    fun getBadge(codeOrName: String?): String {
        if (codeOrName.isNullOrBlank()) return "EN"
        val clean = codeOrName.trim()
        val lower = clean.lowercase(Locale.ROOT)
        return when (lower) {
            "all", "multi", "none", "universal" -> "MULTI"
            "pt-br", "pt_br" -> "PT-BR"
            "zh-cn" -> "ZH-CN"
            "zh-tw" -> "ZH-TW"
            else -> if (clean.length <= 4) clean.uppercase(Locale.ROOT) else clean.take(3).uppercase(Locale.ROOT)
        }
    }
}

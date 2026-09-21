package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile

val INFER_TYPE: ExtractorLinkType = ExtractorLinkType.VIDEO

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null) return Qualities.Unknown.value
    val num = Regex("(\\d{3,4})").find(qualityName)?.value?.toIntOrNull()
    return num ?: when {
        qualityName.contains("4k", ignoreCase = true) || qualityName.contains("2160", ignoreCase = true) -> Qualities.P2160.value
        qualityName.contains("1080", ignoreCase = true) || qualityName.contains("fhd", ignoreCase = true) -> Qualities.P1080.value
        qualityName.contains("720", ignoreCase = true) || qualityName.contains("hd", ignoreCase = true) -> Qualities.P720.value
        qualityName.contains("480", ignoreCase = true) || qualityName.contains("sd", ignoreCase = true) -> Qualities.P480.value
        qualityName.contains("360", ignoreCase = true) -> Qualities.P360.value
        qualityName.contains("240", ignoreCase = true) -> Qualities.P240.value
        qualityName.contains("144", ignoreCase = true) -> Qualities.P144.value
        else -> Qualities.Unknown.value
    }
}

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

fun getPacked(string: String): String? {
    val regex = Regex("""eval\(function\(p,a,c,k,e,[rd]\).*?\.split\(['"]\|['"]\).*?\)\)""", setOf(RegexOption.DOT_MATCHES_ALL))
    return regex.find(string)?.value
}

fun getAndUnpack(string: String): String {
    val packed = getPacked(string) ?: string
    return unpack(packed)
}

private fun unpack(packed: String): String {
    val regex = Regex("""\}\s*\(\s*(['"])(.*?)\1\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(['"])(.*?)\5\.split\(['"]\|['"]\)""", setOf(RegexOption.DOT_MATCHES_ALL))
    val match = regex.find(packed) ?: return packed
    val payload = match.groupValues[2]
    val radix = match.groupValues[3].toIntOrNull() ?: 36
    val keywords = match.groupValues[6].split("|")

    val wordRegex = Regex("""\b\w+\b""")
    return wordRegex.replace(payload) { m ->
        val word = m.value
        val idx = baseDecode(word, radix)
        if (idx != null && idx in keywords.indices && keywords[idx].isNotEmpty()) {
            keywords[idx]
        } else {
            word
        }
    }
}

private fun baseDecode(numStr: String, base: Int): Int? {
    if (base < 2 || base > 62) return null
    var result = 0
    for (ch in numStr) {
        val digit = when (ch) {
            in '0'..'9' -> ch - '0'
            in 'a'..'z' -> ch - 'a' + 10
            in 'A'..'Z' -> ch - 'A' + 36
            else -> return null
        }
        if (digit >= base) return null
        result = result * base + digit
    }
    return result
}

suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType = INFER_TYPE,
    initializer: suspend ExtractorLink.() -> Unit = {}
): ExtractorLink {
    val link = ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        type = type
    )
    initializer(link)
    return link
}

suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit = {},
    callback: (ExtractorLink) -> Unit
): Boolean {
    val cleanUrl = url.trim()
    val cleanNoProtocol = cleanUrl.removePrefix("https://").removePrefix("http://").removePrefix("//").trimEnd('/')
    val extractors = APIHolder.extractorApis.toList().reversed()
    var extracted = false
    for (extractor in extractors) {
        val mainUrl = extractor.mainUrl
        val isMatch = if (mainUrl.isBlank()) false else {
            val domains = mainUrl.split(",").map {
                it.trim().removePrefix("https://").removePrefix("http://").removePrefix("//").trimEnd('/')
            }
            domains.any { domain ->
                domain.isNotBlank() && (cleanNoProtocol.startsWith(domain) || cleanNoProtocol.contains(domain))
            }
        }
        if (isMatch) {
            try {
                extractor.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
                extracted = true
            } catch (t: Throwable) {
                Log.w("ExtractorApi", "Extractor ${extractor.name} failed for $cleanUrl", t)
            }
        }
    }

    // Fallback: If no matched extractor produced links, attempt extractors whose mainUrl is blank/generic
    if (!extracted) {
        for (extractor in extractors) {
            if (extractor.mainUrl.isBlank()) {
                try {
                    extractor.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
                    extracted = true
                } catch (_: Throwable) {}
            }
        }
    }

    return extracted
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return loadExtractor(url, null, subtitleCallback, callback)
}

abstract class ExtractorApi {
    open val name: String = ""
    open val mainUrl: String = ""
    open val requiresReferer: Boolean = false
    open var sourcePlugin: String? = null

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ) {}

    open suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (_: Throwable) {}
    }
}


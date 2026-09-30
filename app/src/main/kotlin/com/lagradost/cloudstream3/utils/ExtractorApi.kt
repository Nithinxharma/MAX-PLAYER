package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import kotlinx.coroutines.CancellationException

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
    val jsUnpacked = JsUnpacker(packed).unpack()
    if (!jsUnpacked.isNullOrBlank()) {
        return jsUnpacked
    }
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

fun ExtractorApi.fixUrl(url: String): String {
    if (url.startsWith("http") || url.startsWith("{\"")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }
    return if (url.startsWith("//")) {
        "https:$url"
    } else if (url.startsWith('/')) {
        mainUrl + url
    } else {
        "$mainUrl/$url"
    }
}

fun getExtractorApiFromName(name: String): ExtractorApi? {
    val apis = APIHolder.extractorApis
    return apis.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: apis.firstOrNull()
}

fun requireReferer(name: String): Boolean {
    return getExtractorApiFromName(name)?.requiresReferer ?: false
}

private val schemaStripRegex = Regex("""^(https?:)?//(www\.)?""")

fun getExtractorForUrl(url: String): ExtractorApi? {
    val cleanUrl = url.trim()
    val compareUrl = cleanUrl.lowercase().replace(schemaStripRegex, "").trimEnd('/')
    val extractors = APIHolder.extractorApis.toList().reversed()

    // 1. Direct domain / prefix matching
    for (extractor in extractors) {
        val mainUrl = extractor.mainUrl
        if (mainUrl.isBlank()) continue
        val domains = mainUrl.split(",").map {
            it.trim().lowercase().replace(schemaStripRegex, "").trimEnd('/').trimEnd('*').trimEnd('.')
        }
        val isMatch = domains.any { domain ->
            domain.isNotBlank() && (compareUrl.startsWith(domain) || compareUrl.contains(domain))
        }
        if (isMatch) return extractor
    }

    // 2. Levenshtein mirror matching
    for (extractor in extractors) {
        val cleanMain = extractor.mainUrl.lowercase().replace(schemaStripRegex, "").substringBefore('/').trimEnd('*').trimEnd('.')
        val cleanHost = compareUrl.substringBefore('/')
        if (cleanMain.isNotBlank() && cleanHost.isNotBlank()) {
            val ratio = Levenshtein.partialRatio(cleanMain, cleanHost)
            if (ratio > 80) return extractor
        }
    }

    return null
}

suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val cleanUrl = url.trim()
    val compareUrl = cleanUrl.lowercase().replace(schemaStripRegex, "").trimEnd('/')
    val extractors = APIHolder.extractorApis.toList().reversed()
    var extracted = false

    // 1. Direct domain / prefix matching
    for (extractor in extractors) {
        val mainUrl = extractor.mainUrl
        if (mainUrl.isBlank()) continue
        val domains = mainUrl.split(",").map {
            it.trim().lowercase().replace(schemaStripRegex, "").trimEnd('/')
        }
        val isMatch = domains.any { domain ->
            domain.isNotBlank() && (compareUrl.startsWith(domain) || compareUrl.contains(domain))
        }
        if (isMatch) {
            try {
                extractor.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
                extracted = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("ExtractorApi", "Extractor ${extractor.name} failed for $cleanUrl", e)
            }
        }
    }

    // 2. Levenshtein mirror matching (for mirrors like example.sx, example.to, example.me)
    if (!extracted) {
        for (extractor in extractors) {
            val cleanMain = extractor.mainUrl.lowercase().replace(schemaStripRegex, "").substringBefore('/')
            val cleanHost = compareUrl.substringBefore('/')
            if (cleanMain.isNotBlank() && cleanHost.isNotBlank()) {
                val ratio = Levenshtein.partialRatio(cleanMain, cleanHost)
                if (ratio > 80) {
                    try {
                        extractor.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
                        extracted = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }
            }
        }
    }

    // 3. Fallback: Universal / generic extractors
    if (!extracted) {
        for (extractor in extractors) {
            if (extractor.mainUrl.isBlank()) {
                try {
                    extractor.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
                    extracted = true
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
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

suspend fun loadExtractor(
    url: String,
    referer: String?,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return loadExtractor(url, referer, {}, callback)
}

suspend fun loadExtractor(
    url: String,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return loadExtractor(url, null, {}, callback)
}

abstract class ExtractorApi {
    open val name: String = ""
    open val mainUrl: String = ""
    open val requiresReferer: Boolean = false
    open var sourcePlugin: String? = null

    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    open suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("ExtractorApi", "Extractor $name failed for $url: ${e.message}")
        }
    }

    open fun getExtractorUrl(id: String): String = id

    companion object {
        fun getExtractorForUrl(url: String): ExtractorApi? = com.lagradost.cloudstream3.utils.getExtractorForUrl(url)
    }
}



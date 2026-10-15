@file:Suppress("DEPRECATION")
package xyz.mpv.rex.maxstream.engine

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.PlayListItem
import java.util.Collections
import java.util.WeakHashMap

// --- Extension Properties & Helpers ---

val Score.score: Double get() = this.toDouble()
val Score.toOld: Int get() = this.toInt()

val AudioFile.lang: String get() = headers?.get("lang") ?: "und"

val PlayListItem.isM3u8: Boolean get() = this.url.contains(".m3u8", ignoreCase = true)

val com.lagradost.cloudstream3.TrailerData.url: String get() = this.extractorUrl

private val extractorSourceMap = Collections.synchronizedMap(WeakHashMap<ExtractorApi, String>())
var ExtractorApi.sourcePlugin: String?
    get() = extractorSourceMap[this]
    set(value) {
        if (value != null) extractorSourceMap[this] = value else extractorSourceMap.remove(this)
    }

// --- MainAPI Extensions ---

suspend fun MainAPI.searchSafe(query: String): List<SearchResponse> {
    return try {
        search(query) ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }
}

fun MainAPI.fixUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        mainUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
}

suspend fun MainAPI.loadMainPage(page: Int = 1, name: String? = null): HomePageResponse? {
    return try {
        val request = MainPageRequest(name ?: this.name, name ?: this.mainUrl, false)
        getMainPage(page, request)
    } catch (_: Throwable) {
        null
    }
}

fun LoadResponse.addTMDbId(id: String?) {
    if (id != null) {
        val current = syncData.toMutableMap()
        current["tmdb"] = id
        syncData = current
    }
}

fun LoadResponse.addTMDbId(id: Int?) {
    if (id != null) addTMDbId(id.toString())
}

fun LoadResponse.addImdbId(id: String?) {
    if (id != null) {
        val current = syncData.toMutableMap()
        current["imdb"] = id
        syncData = current
    }
}

fun LoadResponse.addImdbId(id: Int?) {
    if (id != null) addImdbId(id.toString())
}

// --- APIHolder Extensions ---

fun APIHolder.getApi(name: String): MainAPI? = getApiFromNameNull(name)
fun APIHolder.getApiFromName(name: String): MainAPI? = getApiFromNameNull(name)

val APIHolder.extractorApis: com.lagradost.cloudstream3.utils.AtomicMutableList<ExtractorApi>
    get() = com.lagradost.cloudstream3.utils.extractorApis

fun APIHolder.addPlugin(api: MainAPI) {
    addPluginMapping(api)
    if (!allProviders.contains(api)) {
        allProviders.add(api)
    }
}

fun APIHolder.addExtractor(extractor: ExtractorApi) {
    if (!com.lagradost.cloudstream3.utils.extractorApis.contains(extractor)) {
        com.lagradost.cloudstream3.utils.extractorApis.add(extractor)
    }
}

fun getExtractorForUrl(url: String): ExtractorApi? {
    return com.lagradost.cloudstream3.utils.extractorApis.firstOrNull { extractor ->
        if (extractor.mainUrl.isBlank()) false
        else extractor.mainUrl.split(",").any { domain ->
            val clean = domain.trim().removePrefix("https://").removePrefix("http://").removePrefix("//")
            clean.isNotEmpty() && url.contains(clean, ignoreCase = true)
        }
    }
}

fun APIHolder.clear() {
    allProviders.clear()
}

object DefaultExtractors {
    fun registerAll() {}
}

fun APIHolder.removePluginsBySource(sourcePath: String) {
    allProviders.removeAll { (it as? Any)?.let { true } ?: false }
}

var APIHolder.onApiAddedListener: ((MainAPI) -> Unit)?
    get() = null
    set(_) {}

var APIHolder.onApiRemovedListener: ((MainAPI) -> Unit)?
    get() = null
    set(_) {}

fun BasePlugin.afterPluginsLoaded() {}

// --- DataStoreHelper Extensions ---

fun DataStoreHelper.removeBookmark(idStr: String?) {
    deleteBookmarkedData(idStr?.hashCode())
}

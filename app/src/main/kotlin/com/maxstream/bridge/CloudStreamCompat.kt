package com.maxstream.bridge

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.PlayListItem

import xyz.mpv.rex.maxstream.engine.searchSafe as engineSearchSafe
import xyz.mpv.rex.maxstream.engine.fixUrl as engineFixUrl
import xyz.mpv.rex.maxstream.engine.loadMainPage as engineLoadMainPage
import xyz.mpv.rex.maxstream.engine.addTMDbId as engineAddTMDbId
import xyz.mpv.rex.maxstream.engine.addImdbId as engineAddImdbId
import xyz.mpv.rex.maxstream.engine.addPlugin as engineAddPlugin
import xyz.mpv.rex.maxstream.engine.addExtractor as engineAddExtractor
import xyz.mpv.rex.maxstream.engine.getExtractorForUrl as engineGetExtractorForUrl
import xyz.mpv.rex.maxstream.engine.clear as engineClear
import xyz.mpv.rex.maxstream.engine.removePluginsBySource as engineRemovePluginsBySource
import xyz.mpv.rex.maxstream.engine.removeBookmark as engineRemoveBookmark
import xyz.mpv.rex.maxstream.engine.score as engineScore
import xyz.mpv.rex.maxstream.engine.toOld as engineToOld
import xyz.mpv.rex.maxstream.engine.lang as engineLang
import xyz.mpv.rex.maxstream.engine.isM3u8 as engineIsM3u8
import xyz.mpv.rex.maxstream.engine.url as engineUrl
import xyz.mpv.rex.maxstream.engine.sourcePlugin as engineSourcePlugin

typealias DefaultExtractors = xyz.mpv.rex.maxstream.engine.DefaultExtractors

val Score.score: Double get() = this.engineScore
val Score.toOld: Int get() = this.engineToOld

val AudioFile.lang: String get() = this.engineLang

val PlayListItem.isM3u8: Boolean get() = this.engineIsM3u8

val com.lagradost.cloudstream3.TrailerData.url: String get() = this.engineUrl

var ExtractorApi.sourcePlugin: String?
    get() = this.engineSourcePlugin
    set(value) {
        this.engineSourcePlugin = value
    }

suspend fun MainAPI.searchSafe(query: String): List<SearchResponse> =
    this.engineSearchSafe(query)

fun MainAPI.fixUrl(url: String): String =
    this.engineFixUrl(url)

suspend fun MainAPI.loadMainPage(page: Int = 1, name: String? = null): HomePageResponse? =
    this.engineLoadMainPage(page, name)

fun LoadResponse.addTMDbId(id: String?) =
    this.engineAddTMDbId(id)

fun LoadResponse.addTMDbId(id: Int?) =
    this.engineAddTMDbId(id)

fun LoadResponse.addImdbId(id: String?) =
    this.engineAddImdbId(id)

fun LoadResponse.addImdbId(id: Int?) =
    this.engineAddImdbId(id)

fun APIHolder.getApi(name: String): MainAPI? = getApiFromNameNull(name)
fun APIHolder.getApiFromName(name: String): MainAPI? = getApiFromNameNull(name)

val APIHolder.extractorApis: com.lagradost.cloudstream3.utils.AtomicMutableList<ExtractorApi>
    get() = com.lagradost.cloudstream3.utils.extractorApis

fun APIHolder.addPlugin(api: MainAPI) =
    this.engineAddPlugin(api)

fun APIHolder.addExtractor(extractor: ExtractorApi) =
    this.engineAddExtractor(extractor)

fun getExtractorForUrl(url: String): ExtractorApi? =
    engineGetExtractorForUrl(url)

fun APIHolder.clear() =
    this.engineClear()

fun APIHolder.removePluginsBySource(sourcePath: String) =
    this.engineRemovePluginsBySource(sourcePath)

var APIHolder.onApiAddedListener: ((MainAPI) -> Unit)?
    get() = null
    set(_) {}

var APIHolder.onApiRemovedListener: ((MainAPI) -> Unit)?
    get() = null
    set(_) {}

fun BasePlugin.afterPluginsLoaded() {}

fun DataStoreHelper.removeBookmark(idStr: String?) =
    this.engineRemoveBookmark(idStr)




package com.lagradost.cloudstream3.plugins

import android.content.Context
import androidx.annotation.Keep
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi

const val PLUGIN_TAG = "Plugin"

@Keep
abstract class BasePlugin {
    var filename: String? = null
    var openSettings: ((context: Context) -> Unit)? = null

    @Keep
    class Manifest {
        var name: String? = null
        var pluginClassName: String = ""
        var version: Int? = null
        var requiresResources: Boolean = false
    }

    open fun load(context: Context) {
        load()
    }

    open fun load() {}

    open fun unload() {}

    open fun beforeUnload() {}

    open fun afterPluginsLoaded() {}

    fun registerMainAPI(element: MainAPI) {
        android.util.Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 12: Hooked registerMainAPI() invocation for provider '${element.name}' (mainUrl='${element.mainUrl}') from sourcePlugin='$filename'")
        element.sourcePlugin = this.filename
        APIHolder.addPlugin(element)
    }

    fun registerExtractorAPI(element: ExtractorApi) {
        android.util.Log.i("ExtensionManager", "EXTENSION_AUDIT: Step 12: Hooked registerExtractorAPI() invocation for extractor '${element.name}' (mainUrl='${element.mainUrl}') from sourcePlugin='$filename'")
        element.sourcePlugin = this.filename
        APIHolder.addExtractor(element)
    }
}


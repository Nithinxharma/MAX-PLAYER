package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi


abstract class BasePlugin {
    var filename: String? = null

    class Manifest {
        var name: String? = null
        var pluginClassName: String = ""
        var version: Int? = null
        var requiresResources: Boolean = false
    }

    open fun load() {}
    
    open fun beforeUnload() {}

    open fun afterPluginsLoaded() {}

    fun registerMainAPI(element: MainAPI) {
        element.sourcePlugin = this.filename
        APIHolder.addPlugin(element)
    }

    fun registerExtractorAPI(element: ExtractorApi) {
        element.sourcePlugin = this.filename
        APIHolder.addExtractor(element)
    }
}

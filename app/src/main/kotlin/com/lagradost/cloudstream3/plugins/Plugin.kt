package com.lagradost.cloudstream3.plugins

import android.content.Context

abstract class Plugin {
    var filename: String = ""
    var openSettings: ((Context) -> Unit)? = null

    open fun load(context: Context) {}
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

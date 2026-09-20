package com.lagradost.cloudstream3.actions

import android.content.Context

abstract class VideoClickAction {
    open val name: String = ""
    var sourcePlugin: String? = null
    open fun onClick(context: Context) {}
}

object VideoClickActionHolder {
    val allVideoClickActions = mutableListOf<VideoClickAction>()
}

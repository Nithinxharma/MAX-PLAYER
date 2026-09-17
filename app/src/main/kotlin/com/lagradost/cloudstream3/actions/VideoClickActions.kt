package com.lagradost.cloudstream3.actions

abstract class VideoClickAction {
    open val name: String = ""
    var sourcePlugin: String? = null
}

object VideoClickActionHolder {
    val allVideoClickActions = mutableListOf<VideoClickAction>()
}

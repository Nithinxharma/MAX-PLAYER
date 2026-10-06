package com.lagradost.cloudstream3.utils

import androidx.annotation.Keep

@Keep
object UnpackUtils {
    fun getAndUnpack(string: String): String = com.lagradost.cloudstream3.utils.getAndUnpack(string)
    fun getPacked(string: String): String? = com.lagradost.cloudstream3.utils.getPacked(string)
}

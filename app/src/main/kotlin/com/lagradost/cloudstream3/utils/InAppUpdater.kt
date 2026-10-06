package com.lagradost.cloudstream3.utils

import android.app.Activity
import androidx.annotation.Keep

@Keep
object InAppUpdater {
    data class GithubRelease(
        val tagName: String,
        val body: String,
        val downloadUrl: String
    )

    fun runAutoUpdate(activity: Activity? = null, checkPreRelease: Boolean = false): Boolean {
        return false
    }

    suspend fun getRelease(checkPreRelease: Boolean = false): GithubRelease? {
        return null
    }
}

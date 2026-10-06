package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.annotation.Keep
import com.lagradost.cloudstream3.AcraApplication

@Keep
object AppContextUtils {
    fun getAppContext(): Context {
        return AcraApplication.context
    }
}

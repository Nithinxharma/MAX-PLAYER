package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import androidx.annotation.Keep
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.CommonActivity

@Keep
object UIHelper {
    fun showToast(message: String) {
        CommonActivity.showToast(message)
    }

    fun showToast(context: Context?, message: String) {
        CommonActivity.showToast(message)
    }

    fun showToast(activity: Activity?, message: Int) {
        val str = activity?.getString(message) ?: AcraApplication.context.getString(message)
        CommonActivity.showToast(str)
    }
}

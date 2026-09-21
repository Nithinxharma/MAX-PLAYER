package com.lagradost.cloudstream3

import android.os.Handler
import android.os.Looper
import android.widget.Toast

object CommonActivity {
    val activity: android.app.Activity?
        get() = runCatching { xyz.mpv.rex.App.currentActivity }.getOrNull()

    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(AcraApplication.context, message, duration).show()
            } catch (_: Throwable) {}
        }
    }

    fun showToast(message: com.lagradost.cloudstream3.utils.UiText?, duration: Int = Toast.LENGTH_SHORT) {
        val str = message?.asStringNull(AcraApplication.context) ?: return
        showToast(str, duration)
    }
}

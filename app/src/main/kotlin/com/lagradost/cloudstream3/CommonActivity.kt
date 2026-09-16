package com.lagradost.cloudstream3

import android.os.Handler
import android.os.Looper
import android.widget.Toast

object CommonActivity {
    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(AcraApplication.context, message, duration).show()
            } catch (_: Throwable) {}
        }
    }
}

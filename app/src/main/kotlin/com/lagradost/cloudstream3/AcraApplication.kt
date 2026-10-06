package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.Keep

@Keep
@SuppressLint("StaticFieldLeak")
open class AcraApplication {
    companion object {
        private var _context: Context? = null
        
        val context: Context
            get() = _context 
                ?: xyz.mpv.rex.App.instance.applicationContext 
                ?: throw IllegalStateException("AcraApplication context has not been initialized")

        fun init(appContext: Context) {
            _context = appContext.applicationContext
        }

        fun setContext(appContext: Context) {
            _context = appContext.applicationContext
        }

        fun getKey(folder: String, path: String): String? {
            return CloudStreamApp.getKey<String>(folder, path)
        }

        fun setKey(folder: String, path: String, value: Any) {
            CloudStreamApp.setKey(folder, path, value)
        }

        fun removeKey(folder: String, path: String) {
            CloudStreamApp.removeKey(folder, path)
        }

        fun openBrowser(url: String) {
            CloudStreamApp.openBrowser(url)
        }
    }
}

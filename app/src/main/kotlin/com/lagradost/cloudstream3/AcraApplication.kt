package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.Keep

@Keep
@SuppressLint("StaticFieldLeak")
open class AcraApplication : Application() {

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        setContext(this)
    }

    companion object {
        private var _context: Context? = null

        val context: Context
            get() = _context
                ?: (try { xyz.mpv.rex.App.instance.applicationContext } catch (_: Throwable) { null })
                ?: (try { xyz.mpv.rex.MaxStreamApp.instance.applicationContext } catch (_: Throwable) { null })
                ?: (try { com.lagradost.cloudstream3.CommonActivity.activity?.applicationContext } catch (_: Throwable) { null })
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

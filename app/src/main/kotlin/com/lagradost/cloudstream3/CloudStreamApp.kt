package com.lagradost.cloudstream3

import android.content.Context
import com.fasterxml.jackson.module.kotlin.readValue

class CloudStreamApp {
    companion object {
        val context: Context? get() = try { AcraApplication.context } catch (_: Throwable) { null }

        inline fun <reified T> getKey(key: String): T? {
            val ctx = context ?: return null
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString(key, null) ?: return null
            return try {
                mapper.readValue<T>(json)
            } catch (_: Throwable) {
                null
            }
        }

        inline fun <reified T> setKey(key: String, value: T) {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            val json = mapper.writeValueAsString(value)
            prefs.edit().putString(key, json).apply()
        }

        fun removeKey(key: String) {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove(key).apply()
        }
    }
}

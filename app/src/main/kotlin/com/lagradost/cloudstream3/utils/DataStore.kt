package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.AcraApplication

object DataStore {
    private val prefs by lazy {
        AcraApplication.context.getSharedPreferences("cloudstream_plugin_data", Context.MODE_PRIVATE)
    }

    fun setKey(folder: String, key: String, value: Any?) {
        val storageKey = "${folder}_$key"
        with(prefs.edit()) {
            when (value) {
                is String -> putString(storageKey, value)
                is Boolean -> putBoolean(storageKey, value)
                is Int -> putInt(storageKey, value)
                is Long -> putLong(storageKey, value)
                is Float -> putFloat(storageKey, value)
                null -> remove(storageKey)
                else -> putString(storageKey, value.toString())
            }
            apply()
        }
    }

    fun <T> getKey(folder: String, key: String, default: T): T {
        val storageKey = "${folder}_$key"
        if (!prefs.contains(storageKey)) return default
        @Suppress("UNCHECKED_CAST")
        return when (default) {
            is String -> prefs.getString(storageKey, default) as T
            is Boolean -> prefs.getBoolean(storageKey, default) as T
            is Int -> prefs.getInt(storageKey, default) as T
            is Long -> prefs.getLong(storageKey, default) as T
            is Float -> prefs.getFloat(storageKey, default) as T
            else -> default
        }
    }

    fun removeKey(folder: String, key: String) {
        prefs.edit().remove("${folder}_$key").apply()
    }
}

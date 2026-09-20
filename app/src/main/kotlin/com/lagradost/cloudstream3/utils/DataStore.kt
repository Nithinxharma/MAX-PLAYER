package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.AcraApplication

object DataStore {
    private val prefs by lazy {
        AcraApplication.context.getSharedPreferences("cloudstream_plugin_data", Context.MODE_PRIVATE)
    }

    @JvmStatic
    @JvmOverloads
    fun getSharedPrefs(context: Context? = null): SharedPreferences {
        val ctx = context ?: runCatching { AcraApplication.context }.getOrNull()
        return ctx?.getSharedPreferences("cloudstream_plugin_data", Context.MODE_PRIVATE)
            ?: prefs
    }

    @JvmStatic
    fun setKey(folder: String, key: String, value: Any?) {
        val storageKey = "${folder}_$key"
        with(getSharedPrefs().edit()) {
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

    @JvmStatic
    fun setKey(key: String, value: Any?) {
        setKey("default", key, value)
    }

    @JvmStatic
    fun <T> getKey(folder: String, key: String, default: T): T {
        val storageKey = "${folder}_$key"
        val sp = getSharedPrefs()
        if (!sp.contains(storageKey)) return default
        @Suppress("UNCHECKED_CAST")
        return when (default) {
            is String -> sp.getString(storageKey, default) as T
            is Boolean -> sp.getBoolean(storageKey, default) as T
            is Int -> sp.getInt(storageKey, default) as T
            is Long -> sp.getLong(storageKey, default) as T
            is Float -> sp.getFloat(storageKey, default) as T
            else -> default
        }
    }

    @JvmStatic
    fun <T> getKey(key: String, default: T): T {
        return getKey("default", key, default)
    }

    @JvmStatic
    fun <T> getKey(key: String): T? {
        val sp = getSharedPrefs()
        val storageKey = "default_$key"
        if (!sp.contains(storageKey)) return null
        @Suppress("UNCHECKED_CAST")
        return sp.all[storageKey] as? T
    }

    @JvmStatic
    fun removeKey(folder: String, key: String) {
        getSharedPrefs().edit().remove("${folder}_$key").apply()
    }

    @JvmStatic
    fun removeKey(key: String) {
        removeKey("default", key)
    }
}

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.AcraApplication

object DataStore {
    private const val PREFERENCES_NAME = "cloudstream_plugin_data"

    private val defaultPrefs by lazy {
        try {
            AcraApplication.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    fun getSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun getSharedPrefs(context: Context, name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getSharedPrefs(): SharedPreferences {
        val ctx = runCatching { AcraApplication.context }.getOrNull()
        return ctx?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?: defaultPrefs
            ?: throw IllegalStateException("Context not available for SharedPreferences")
    }

    fun setKey(folder: String, key: String, value: Any?) {
        val storageKey = "${folder}_$key"
        val sp = getSharedPrefs()
        with(sp.edit()) {
            when (value) {
                null -> remove(storageKey)
                is String -> putString(storageKey, value)
                is Boolean -> putBoolean(storageKey, value)
                is Int -> putInt(storageKey, value)
                is Long -> putLong(storageKey, value)
                is Float -> putFloat(storageKey, value)
                else -> putString(storageKey, AppUtils.toJson(value))
            }
            apply()
        }
    }

    fun setKey(key: String, value: Any?) {
        setKey("default", key, value)
    }

    fun <T> setKey(context: Context, folder: String, key: String, value: T) {
        val storageKey = "${folder}_$key"
        with(getSharedPrefs(context).edit()) {
            when (value) {
                null -> remove(storageKey)
                is String -> putString(storageKey, value)
                is Boolean -> putBoolean(storageKey, value)
                is Int -> putInt(storageKey, value)
                is Long -> putLong(storageKey, value)
                is Float -> putFloat(storageKey, value)
                else -> putString(storageKey, AppUtils.toJson(value))
            }
            apply()
        }
    }

    fun <T> setKey(context: Context, key: String, value: T) {
        setKey(context, "default", key, value)
    }

    fun <T> getKey(folder: String, key: String, default: T): T {
        val storageKey = "${folder}_$key"
        val sp = getSharedPrefs()
        if (!sp.contains(storageKey)) return default
        @Suppress("UNCHECKED_CAST")
        return when (default) {
            is String -> (sp.getString(storageKey, default) ?: default) as T
            is Boolean -> sp.getBoolean(storageKey, default) as T
            is Int -> sp.getInt(storageKey, default) as T
            is Long -> sp.getLong(storageKey, default) as T
            is Float -> sp.getFloat(storageKey, default) as T
            else -> {
                val str = sp.getString(storageKey, null) ?: return default
                try {
                    AppUtils.mapper.readValue(str, (default as Any)::class.java as Class<T>)
                } catch (_: Throwable) {
                    default
                }
            }
        }
    }

    fun <T> getKey(key: String, default: T): T {
        return getKey("default", key, default)
    }

    fun <T> getKey(context: Context, folder: String, key: String, default: T): T {
        val storageKey = "${folder}_$key"
        val sp = getSharedPrefs(context)
        if (!sp.contains(storageKey)) return default
        @Suppress("UNCHECKED_CAST")
        return when (default) {
            is String -> (sp.getString(storageKey, default) ?: default) as T
            is Boolean -> sp.getBoolean(storageKey, default) as T
            is Int -> sp.getInt(storageKey, default) as T
            is Long -> sp.getLong(storageKey, default) as T
            is Float -> sp.getFloat(storageKey, default) as T
            else -> {
                val str = sp.getString(storageKey, null) ?: return default
                try {
                    AppUtils.mapper.readValue(str, (default as Any)::class.java as Class<T>)
                } catch (_: Throwable) {
                    default
                }
            }
        }
    }

    fun <T> getKey(context: Context, key: String, default: T): T {
        return getKey(context, "default", key, default)
    }

    fun <T> getKey(key: String): T? {
        return getKey("default", key)
    }

    fun <T> getKey(folder: String, key: String): T? {
        val sp = getSharedPrefs()
        val storageKey = "${folder}_$key"
        if (!sp.contains(storageKey)) return null
        @Suppress("UNCHECKED_CAST")
        return sp.all[storageKey] as? T
    }

    fun <T> getKey(context: Context, folder: String, key: String): T? {
        val sp = getSharedPrefs(context)
        val storageKey = "${folder}_$key"
        if (!sp.contains(storageKey)) return null
        @Suppress("UNCHECKED_CAST")
        return sp.all[storageKey] as? T
    }

    fun <T> getKey(context: Context, key: String): T? {
        return getKey(context, "default", key)
    }

    fun removeKey(folder: String, key: String) {
        getSharedPrefs().edit().remove("${folder}_$key").apply()
    }

    fun removeKey(key: String) {
        removeKey("default", key)
    }

    fun removeKey(context: Context, folder: String, key: String) {
        getSharedPrefs(context).edit().remove("${folder}_$key").apply()
    }

    fun removeKey(context: Context, key: String) {
        removeKey(context, "default", key)
    }

    fun containsKey(folder: String, key: String): Boolean {
        return getSharedPrefs().contains("${folder}_$key")
    }

    fun containsKey(key: String): Boolean {
        return containsKey("default", key)
    }

    fun containsKey(context: Context, folder: String, key: String): Boolean {
        return getSharedPrefs(context).contains("${folder}_$key")
    }

    fun containsKey(context: Context, key: String): Boolean {
        return containsKey(context, "default", key)
    }
}

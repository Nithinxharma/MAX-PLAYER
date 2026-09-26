package com.lagradost.cloudstream3

import android.content.Context
import com.fasterxml.jackson.module.kotlin.readValue

class CloudStreamApp {
    companion object {
        val context: Context? get() = try { AcraApplication.context } catch (_: Throwable) { null }

        inline fun <reified T> getKey(key: String): T? {
            val ctx = context ?: return null
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            val str = prefs.getString(key, null) ?: return null
            if (T::class == String::class) {
                if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
                    return try { mapper.readValue<T>(str) } catch (_: Throwable) { str as T }
                }
                return str as T
            }
            return try {
                mapper.readValue<T>(str)
            } catch (_: Throwable) {
                try {
                    val raw: Any? = when (T::class) {
                        Boolean::class -> str.toBooleanStrictOrNull()
                        Int::class -> str.toIntOrNull()
                        Long::class -> str.toLongOrNull()
                        Double::class -> str.toDoubleOrNull()
                        Float::class -> str.toFloatOrNull()
                        else -> null
                    }
                    @Suppress("UNCHECKED_CAST")
                    raw as? T
                } catch (_: Throwable) {
                    null
                }
            }
        }

        inline fun <reified T> getKey(key: String, default: T): T = getKey(key) ?: default

        inline fun <reified T> setKey(key: String, value: T) {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            val str = if (value is String) value else mapper.writeValueAsString(value)
            prefs.edit().putString(key, str).apply()
        }

        inline fun <reified T> getKey(folder: String, key: String): T? = getKey("$folder/$key")
        inline fun <reified T> getKey(folder: String, key: String, default: T): T = getKey("$folder/$key") ?: default
        inline fun <reified T> setKey(folder: String, key: String, value: T) = setKey("$folder/$key", value)

        fun removeKey(key: String) {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove(key).apply()
        }

        fun removeKey(folder: String, key: String) = removeKey("$folder/$key")

        fun getKeys(folder: String): List<String>? {
            val ctx = context ?: return null
            val prefs = ctx.getSharedPreferences("cloudstream_app_prefs", Context.MODE_PRIVATE)
            return prefs.all.keys.filter { it.startsWith("$folder/") }
        }

        fun openBrowser(url: String) {
            val ctx = context ?: return
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}

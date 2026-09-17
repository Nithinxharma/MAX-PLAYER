package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import kotlin.reflect.KClass

object AppUtils {
    
    fun Any.toJson(): String {
        return mapper.writeValueAsString(this)
    }

    fun Any.toJsonLiteral(): String {
        return mapper.writeValueAsString(this)
    }

    fun <T : Any> parseJson(value: String, kClass: KClass<T>): T {
        return mapper.readValue(value, kClass.java)
    }

    inline fun <reified T : Any> parseJson(value: String): T {
        return mapper.readValue(value)
    }

    inline fun <reified T> tryParseJson(value: String?): T? {
        if (value.isNullOrBlank()) return null
        return try {
            mapper.readValue(value)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun Any.toJson(): String {
    return mapper.writeValueAsString(this)
}

inline fun <reified T> tryParseJson(value: String?): T? {
    if (value.isNullOrBlank()) return null
    return try {
        mapper.readValue(value)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

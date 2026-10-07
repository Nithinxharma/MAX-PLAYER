package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import kotlin.reflect.KClass

val mapper: JsonMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    .build()

object AppUtils {
    val mapper: JsonMapper get() = com.lagradost.cloudstream3.utils.mapper

    fun Any.toJson(): String {
        return com.lagradost.cloudstream3.utils.mapper.writeValueAsString(this)
    }

    fun Any.toJsonLiteral(): String {
        return com.lagradost.cloudstream3.utils.mapper.writeValueAsString(this)
    }

    fun <T : Any> parseJson(value: String, kClass: KClass<T>): T {
        return com.lagradost.cloudstream3.utils.mapper.readValue(value, kClass.java)
    }

    fun <T> parseJson(value: String, clazz: Class<T>): T {
        return com.lagradost.cloudstream3.utils.mapper.readValue(value, clazz)
    }

    fun <T> tryParseJson(value: String?, clazz: Class<T>): T? {
        if (value.isNullOrBlank()) return null
        return try {
            com.lagradost.cloudstream3.utils.mapper.readValue(value, clazz)
        } catch (_: Throwable) {
            null
        }
    }

    inline fun <reified T : Any> parseJson(value: String): T {
        return com.lagradost.cloudstream3.utils.mapper.readValue(value)
    }

    inline fun <reified T> tryParseJson(value: String?): T? {
        if (value.isNullOrBlank()) return null
        return try {
            com.lagradost.cloudstream3.utils.mapper.readValue(value)
        } catch (_: Throwable) {
            null
        }
    }

    inline fun <reified T> String.parsedSafe(): T? {
        return tryParseJson(this)
    }

    inline fun <T> tryParse(call: () -> T): T? {
        return try {
            call()
        } catch (_: Throwable) {
            null
        }
    }

    fun base64Decode(string: String): String = com.lagradost.cloudstream3.base64Decode(string)
    fun base64DecodeArray(string: String): ByteArray = com.lagradost.cloudstream3.base64DecodeArray(string)
    fun base64Encode(bytes: ByteArray): String = com.lagradost.cloudstream3.base64Encode(bytes)
}

fun Any.toJson(): String {
    return mapper.writeValueAsString(this)
}

inline fun <reified T> tryParseJson(value: String?): T? {
    if (value.isNullOrBlank()) return null
    return try {
        mapper.readValue(value)
    } catch (_: Throwable) {
        null
    }
}

fun <T> tryParseJson(value: String?, clazz: Class<T>): T? = AppUtils.tryParseJson(value, clazz)
fun <T : Any> parseJson(value: String, kClass: KClass<T>): T = AppUtils.parseJson(value, kClass)
fun <T> parseJson(value: String, clazz: Class<T>): T = AppUtils.parseJson(value, clazz)
inline fun <reified T : Any> parseJson(value: String): T = AppUtils.parseJson(value)

inline fun <reified T> String.parsedSafe(): T? = tryParseJson<T>(this)

inline fun <T> tryParse(call: () -> T): T? = AppUtils.tryParse(call)

fun base64Decode(string: String): String = com.lagradost.cloudstream3.base64Decode(string)
fun base64DecodeArray(string: String): ByteArray = com.lagradost.cloudstream3.base64DecodeArray(string)
fun base64Encode(bytes: ByteArray): String = com.lagradost.cloudstream3.base64Encode(bytes)

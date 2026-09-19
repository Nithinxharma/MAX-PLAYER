@file:JvmName("MainActivityKt")
package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return parseJson(text, kClass)
    }
    
    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            parse(text, kClass)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun writeValueAsString(obj: Any): String {
        return obj.toJson()
    }
}

var app = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

var insecureApp = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

const val PROVIDER_STATUS_DOWN = 0
const val PROVIDER_STATUS_OK = 1
const val PROVIDER_STATUS_SLOW = 2
const val PROVIDER_STATUS_BETA = 3
const val AllLanguagesName = "All"

enum class AutoDownloadMode {
    All,
    FilterByLang,
    NsfwOnly
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class InternalAPI

open class MainActivity : androidx.fragment.app.FragmentActivity() {
    companion object {
        var afterPluginsLoadedEvent: ((Boolean) -> Unit) = {}
        var lastError: String? = null
    }
}

package com.lagradost.nicehttp

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

open class Requests(
    val baseClient: OkHttpClient = defaultClient
) {
    companion object {
        private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

        val defaultCookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val existing = cookieStore.getOrPut(host) { mutableListOf() }
                synchronized(existing) {
                    cookies.forEach { newCookie ->
                        existing.removeAll { it.name == newCookie.name }
                        existing.add(newCookie)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val host = url.host
                return cookieStore[host]?.toList() ?: emptyList()
            }
        }

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .cookieJar(defaultCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    var customHeaders: MutableMap<String, String> = mutableMapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "*/*"
    )

    private fun buildUrl(url: String, params: Map<String, String>): String {
        if (params.isEmpty()) return url
        val httpUrl = url.toHttpUrlOrNull()?.newBuilder() ?: return url
        for ((k, v) in params) {
            httpUrl.addQueryParameter(k, v)
        }
        return httpUrl.build().toString()
    }

    private fun buildHeaders(
        headers: Map<String, String>,
        referer: String?,
        cookies: Map<String, String>
    ): okhttp3.Headers {
        val merged = customHeaders.toMutableMap()
        merged.putAll(headers)
        if (!referer.isNullOrBlank()) {
            merged["Referer"] = referer
        }
        if (cookies.isNotEmpty()) {
            val cookieStr = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            merged["Cookie"] = cookieStr
        }
        return merged.toHeaders()
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        timeout: Long = 30L,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0
    ): NiceResponse = withContext(Dispatchers.IO) {
        val finalUrl = buildUrl(url, params)
        val requestBuilder = Request.Builder()
            .url(finalUrl)
            .headers(buildHeaders(headers, referer, cookies))
            .get()

        val client = if (timeout != 30L || !allowRedirects) {
            baseClient.newBuilder()
                .readTimeout(timeout, TimeUnit.SECONDS)
                .followRedirects(allowRedirects)
                .build()
        } else {
            baseClient
        }

        val response = client.newCall(requestBuilder.build()).execute()
        NiceResponse(response)
    }

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        timeout: Long = 30L,
        allowRedirects: Boolean = true
    ): NiceResponse = withContext(Dispatchers.IO) {
        val finalUrl = buildUrl(url, params)
        val body: RequestBody = when {
            requestBody != null -> requestBody
            data != null -> {
                val form = FormBody.Builder()
                for ((k, v) in data) {
                    form.add(k, v)
                }
                form.build()
            }
            json != null -> {
                val jsonStr = if (json is String) json else json.toJson()
                jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            }
            else -> "".toRequestBody(null)
        }

        val requestBuilder = Request.Builder()
            .url(finalUrl)
            .headers(buildHeaders(headers, referer, cookies))
            .post(body)

        val client = if (timeout != 30L || !allowRedirects) {
            baseClient.newBuilder()
                .readTimeout(timeout, TimeUnit.SECONDS)
                .followRedirects(allowRedirects)
                .build()
        } else {
            baseClient
        }

        val response = client.newCall(requestBuilder.build()).execute()
        NiceResponse(response)
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        timeout: Long = 30L,
        allowRedirects: Boolean = true
    ): NiceResponse = withContext(Dispatchers.IO) {
        val finalUrl = buildUrl(url, params)
        val requestBuilder = Request.Builder()
            .url(finalUrl)
            .headers(buildHeaders(headers, referer, cookies))
            .head()

        val response = baseClient.newCall(requestBuilder.build()).execute()
        NiceResponse(response)
    }
}

class Session : Requests()

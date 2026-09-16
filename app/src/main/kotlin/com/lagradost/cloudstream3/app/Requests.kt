package com.lagradost.cloudstream3.app

import com.lagradost.cloudstream3.CloudstreamHttp
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class NiceResponse(
    val text: String,
    val url: String,
    val code: Int,
    val headers: Headers
)

object app {
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String> = emptyMap()
    ): NiceResponse {
        var finalUrl = url
        if (params.isNotEmpty()) {
            val query = params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
            finalUrl = if (url.contains("?")) "$url&$query" else "$url?$query"
        }

        val requestBuilder = Request.Builder().url(finalUrl)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        CloudstreamHttp.client.newCall(requestBuilder.build()).execute().use { response ->
            return NiceResponse(
                text = response.body?.string() ?: "",
                url = response.request.url.toString(),
                code = response.code,
                headers = response.headers
            )
        }
    }

    fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        data: Map<String, String> = emptyMap(),
        requestBody: String? = null
    ): NiceResponse {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        val body = when {
            requestBody != null -> requestBody.toRequestBody("application/json".toMediaTypeOrNull())
            data.isNotEmpty() -> {
                val form = okhttp3.FormBody.Builder()
                data.forEach { (k, v) -> form.add(k, v) }
                form.build()
            }
            else -> "".toRequestBody(null)
        }
        requestBuilder.post(body)

        CloudstreamHttp.client.newCall(requestBuilder.build()).execute().use { response ->
            return NiceResponse(
                text = response.body?.string() ?: "",
                url = response.request.url.toString(),
                code = response.code,
                headers = response.headers
            )
        }
    }
}

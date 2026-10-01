package com.lagradost.nicehttp

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.tryParseJson
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

typealias Response = NiceResponse

class NiceResponse(
    val okhttpResponse: Response
) {
    val code: Int = okhttpResponse.code
    val isSuccessful: Boolean = okhttpResponse.isSuccessful
    val headers: Headers = okhttpResponse.headers
    val url: String = okhttpResponse.request.url.toString()
    val okhttpUrl: HttpUrl = okhttpResponse.request.url
    val body: ResponseBody? = okhttpResponse.body

    val text: String by lazy {
        try {
            body?.string() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    val document: Document by lazy {
        Jsoup.parse(text, url)
    }

    inline fun <reified T : Any> parsed(): T {
        return mapper.readValue(text)
    }

    inline fun <reified T : Any> parsedSafe(): T? {
        return tryParseJson(text)
    }
}

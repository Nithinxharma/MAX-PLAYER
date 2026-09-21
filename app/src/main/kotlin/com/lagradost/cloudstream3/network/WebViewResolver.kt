package com.lagradost.cloudstream3.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = true,
    val script: String? = null,
    val scriptCallback: ((String) -> Unit)? = null,
    val timeout: Long = 10000L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (!interceptUrl.containsMatchIn(url) && additionalUrls.none { it.containsMatchIn(url) }) {
            return chain.proceed(request)
        }

        Log.i("WebViewResolver", "WebViewResolver triggered for URL: $url")
        var resolvedUrl: String? = null
        var resolvedCookies: String? = null
        var scriptOutput: String? = null
        val latch = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                val context = AcraApplication.context
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.userAgentString = userAgent ?: USER_AGENT
                    settings.domStorageEnabled = true
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        resolvedUrl = finishedUrl
                        resolvedCookies = CookieManager.getInstance().getCookie(finishedUrl ?: url)

                        if (!script.isNullOrBlank()) {
                            webView.evaluateJavascript(script) { result ->
                                scriptOutput = result
                                scriptCallback?.invoke(result ?: "")
                                latch.countDown()
                                webView.destroy()
                            }
                        } else {
                            latch.countDown()
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(url)
            } catch (e: Throwable) {
                Log.e("WebViewResolver", "Error executing WebViewResolver for $url", e)
                latch.countDown()
            }
        }

        runCatching {
            latch.await(timeout, TimeUnit.MILLISECONDS)
        }

        if (useOkhttp && !resolvedUrl.isNullOrBlank()) {
            val newRequestBuilder = request.newBuilder().url(resolvedUrl!!)
            if (!resolvedCookies.isNullOrBlank()) {
                newRequestBuilder.header("Cookie", resolvedCookies!!)
            }
            if (!userAgent.isNullOrBlank()) {
                newRequestBuilder.header("User-Agent", userAgent)
            }
            return chain.proceed(newRequestBuilder.build())
        }

        return chain.proceed(request)
    }
}

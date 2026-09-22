package com.lagradost.cloudstream3.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * @param alwaysBypass will pre-emptively fetch ddos guard cookies if true.
 * If false it will only try to get cookies when a request returns 403/503 or challenge header.
 */
@AnyThread
class DdosGuardKiller(private val alwaysBypass: Boolean = false) : Interceptor {

    companion object {
        private const val TAG = "DdosGuardKiller"
        val savedCookiesMap = ConcurrentHashMap<String, Map<String, String>>()
        @Volatile private var ddosBypassPath: String? = null
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host

        // Check if we already have saved cookies for this host and request doesn't have them
        val savedCookies = savedCookiesMap[host]
        val activeRequest = if (!savedCookies.isNullOrEmpty() && request.header("Cookie") == null) {
            val cookieHeader = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            request.newBuilder()
                .header("Cookie", cookieHeader)
                .build()
        } else {
            request
        }

        if (alwaysBypass && savedCookies.isNullOrEmpty()) {
            return@runBlocking bypassDdosGuard(chain, activeRequest)
        }

        val response = chain.proceed(activeRequest)
        if (isDdosGuardChallenge(response)) {
            response.close()
            Log.i(TAG, "DDOS-GUARD challenge encountered on ${request.url.host}. Resolving bypass...")
            return@runBlocking bypassDdosGuard(chain, activeRequest)
        }

        response
    }

    private fun isDdosGuardChallenge(response: Response): Boolean {
        val code = response.code
        if (code != 403 && code != 503 && code != 429) return false

        val serverHeader = response.header("Server").orEmpty()
        if (serverHeader.contains("ddos-guard", ignoreCase = true) || serverHeader.contains("ddg", ignoreCase = true)) {
            return true
        }

        return try {
            val snippet = response.peekBody(2048).string()
            snippet.contains("ddos-guard", ignoreCase = true) ||
                    snippet.contains("check.ddos-guard.net", ignoreCase = true) ||
                    snippet.contains("ddg-captcha", ignoreCase = true) ||
                    snippet.contains("DDoS protection by DDOS-GUARD", ignoreCase = true)
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun bypassDdosGuard(chain: Interceptor.Chain, request: Request): Response {
        val host = request.url.host

        // 1. Direct JS Check Path resolution (fastest, standard CloudStream method)
        var cookies = savedCookiesMap[host]
        if (cookies.isNullOrEmpty()) {
            try {
                if (ddosBypassPath.isNullOrBlank()) {
                    val checkScript = app.get("https://check.ddos-guard.net/check.js").text
                    ddosBypassPath = Regex("'(.*?)'").find(checkScript)?.groupValues?.getOrNull(1)
                }

                val bypassUrl = request.url.scheme + "://" + host + (ddosBypassPath ?: "")
                val resp = Requests().get(bypassUrl)
                val respCookies = resp.cookies
                if (respCookies.isNotEmpty()) {
                    savedCookiesMap[host] = respCookies
                    cookies = respCookies
                    Log.i(TAG, "Successfully acquired DDOS-GUARD cookies via direct check for $host")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Direct DDOS-GUARD check failed for $host: ${t.message}. Falling back to WebView...")
            }
        }

        // 2. Headless WebView resolution fallback
        if (cookies.isNullOrEmpty()) {
            val webViewCookies = resolveCookiesWithWebView(request.url.toString())
            if (!webViewCookies.isNullOrBlank()) {
                val parsed = webViewCookies.split(";").mapNotNull {
                    val parts = it.trim().split("=", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                }.toMap()
                if (parsed.isNotEmpty()) {
                    savedCookiesMap[host] = parsed
                    cookies = parsed
                    Log.i(TAG, "Successfully acquired DDOS-GUARD cookies via WebView for $host")
                }
            }
        }

        val cookieString = cookies?.entries?.joinToString("; ") { "${it.key}=${it.value}" }
        val newRequestBuilder = request.newBuilder()
            .header("User-Agent", USER_AGENT)

        if (!cookieString.isNullOrBlank()) {
            newRequestBuilder.header("Cookie", cookieString)
        }

        val newRequest = newRequestBuilder.build()
        return chain.proceed(newRequest)
    }

    private fun resolveCookiesWithWebView(url: String): String? {
        var cookies: String? = null
        val latch = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                val context = AcraApplication.context ?: run {
                    latch.countDown()
                    return@post
                }
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.domStorageEnabled = true
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        val currentCookies = CookieManager.getInstance().getCookie(finishedUrl ?: url)
                        if (!currentCookies.isNullOrBlank() && (currentCookies.contains("__ddg") || currentCookies.contains("ddg"))) {
                            cookies = currentCookies
                            latch.countDown()
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(url)
            } catch (e: Throwable) {
                Log.e(TAG, "WebView error resolving DDOS-GUARD: ${e.message}")
                latch.countDown()
            }
        }

        try {
            latch.await(8, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }

        return cookies
    }
}

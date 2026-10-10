package com.lagradost.cloudstream3.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CloudflareKiller intercepts HTTP 403, 503, and 429 challenge responses
 * and headlessly solves JavaScript/anti-bot verification using Android's WebView.
 */
class CloudflareKiller : Interceptor {
    companion object {
        private const val TAG = "CloudflareKiller"
        private val CHALLENGE_CODES = setOf(403, 503, 429)
        private val CLOUDFLARE_SERVERS = setOf("cloudflare", "cloudflare-nginx", "ddos-guard", "bunny")
        val savedCookies = ConcurrentHashMap<String, String>()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // 1. Inject cached clearance cookies if present
        var effectiveRequest = request
        savedCookies[host]?.let { cachedCookies ->
            effectiveRequest = request.newBuilder()
                .header("Cookie", cachedCookies)
                .header("User-Agent", USER_AGENT)
                .build()
        }

        val response = chain.proceed(effectiveRequest)

        // 2. Detect anti-bot challenge signatures
        val server = response.header("Server")?.lowercase() ?: ""
        val isChallenge = response.code in CHALLENGE_CODES && (
            CLOUDFLARE_SERVERS.any { server.contains(it) } ||
            response.header("cf-ray") != null ||
            response.header("cf-mitigated") != null ||
            response.peekBody(2048).string().contains("Just a moment...", ignoreCase = true) ||
            response.peekBody(2048).string().contains("DDoS-GUARD", ignoreCase = true) ||
            response.peekBody(2048).string().contains("Attention Required", ignoreCase = true)
        )

        if (!isChallenge) {
            return response
        }

        Log.i(TAG, "Anti-bot challenge encountered on $host (${response.code}). Attempting WebView clearance...")
        response.close()

        // 3. Resolve cookies via Headless WebView
        val resolvedCookieString = resolveCookiesViaWebView(request.url.toString(), host)
        if (!resolvedCookieString.isNullOrBlank()) {
            savedCookies[host] = resolvedCookieString
            val retriedRequest = request.newBuilder()
                .header("Cookie", resolvedCookieString)
                .header("User-Agent", USER_AGENT)
                .build()
            Log.i(TAG, "Retrying request for $host with valid clearance cookies.")
            return chain.proceed(retriedRequest)
        }

        // Return original response if challenge could not be resolved
        return chain.proceed(request)
    }

    private fun resolveCookiesViaWebView(url: String, host: String): String? {
        var cookies: String? = null
        val latch = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                val context = AcraApplication.context
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        val cookieManager = CookieManager.getInstance()
                        val currentCookies = cookieManager.getCookie(finishedUrl ?: url)
                        if (currentCookies != null && (
                            currentCookies.contains("cf_clearance") ||
                            currentCookies.contains("__cf_bm") ||
                            currentCookies.contains("__ddg2")
                        )) {
                            cookies = currentCookies
                            latch.countDown()
                            webView.destroy()
                        }
                    }
                }
                webView.loadUrl(url)
            } catch (t: Throwable) {
                Log.e(TAG, "WebView challenge resolution failed: ${t.message}", t)
                latch.countDown()
            }
        }

        runCatching {
            latch.await(12, TimeUnit.SECONDS)
        }
        return cookies
    }
}

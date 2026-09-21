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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareKiller : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Check if response is blocked by Cloudflare (403 or 503 with Cloudflare headers/content)
        val code = response.code
        val isCloudflareChallenge = (code == 403 || code == 503 || code == 429) &&
                (response.header("Server")?.contains("cloudflare", ignoreCase = true) == true ||
                        response.header("cf-ray") != null ||
                        response.peekBody(1024).string().contains("cf-browser-verification", ignoreCase = true) ||
                        response.peekBody(2048).string().contains("Just a moment...", ignoreCase = true))

        if (!isCloudflareChallenge) {
            return response
        }

        Log.i("CloudflareKiller", "Cloudflare anti-bot challenge detected for ${request.url}. Attempting WebView resolution...")
        
        val url = request.url.toString()
        val cookies = resolveCookiesWithWebView(url)

        if (!cookies.isNullOrBlank()) {
            response.close()
            val newRequest = request.newBuilder()
                .header("Cookie", cookies)
                .header("User-Agent", USER_AGENT)
                .build()
            Log.i("CloudflareKiller", "Retrying request with Cloudflare cookies...")
            return chain.proceed(newRequest)
        }

        return response
    }

    private fun resolveCookiesWithWebView(url: String): String? {
        var cookies: String? = null
        val latch = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                val context = AcraApplication.context
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.domStorageEnabled = true
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        val cookieMgr = CookieManager.getInstance()
                        val currentCookies = cookieMgr.getCookie(finishedUrl ?: url)
                        if (currentCookies != null && (currentCookies.contains("cf_clearance") || currentCookies.contains("__cf_bm"))) {
                            cookies = currentCookies
                            latch.countDown()
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(url)
            } catch (e: Throwable) {
                Log.e("CloudflareKiller", "WebView challenge resolution failed", e)
                latch.countDown()
            }
        }

        runCatching {
            latch.await(12, TimeUnit.SECONDS)
        }

        return cookies
    }
}

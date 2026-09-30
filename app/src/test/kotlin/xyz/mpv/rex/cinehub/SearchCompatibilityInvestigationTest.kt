package xyz.mpv.rex.cinehub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchCompatibilityInvestigationTest {

    data class SearchRequestTrace(
        val originalQuery: String,
        val queryAfterNormalization: String,
        val queryAfterEncoding: String,
        val generatedRequestUrl: String,
        val requestObject: String,
        val finalUrlSentToServer: String,
        val redirectUrl: String?,
        val responseCode: Int,
        val responseBodyLength: Long,
        val parserInput: String,
        val parserOutputCount: Int
    )

    // Provider A: Modern CloudStream 3 paginated provider (overrides search(query, page))
    class PaginatedCommunityProvider : MainAPI() {
        override var mainUrl = "https://moviesleech.rest"
        override var name = "CommunityPaginatedProvider"
        override var hasMainPage = true
        override var lang = "en"
        override val hasDownloadSupport = true
        override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

        override suspend fun search(query: String, page: Int): SearchResponseList? {
            val url = "$mainUrl/search/$query/page/$page"
            val response = app.get(url)
            val document = response.document
            val results = document.select("div.post-cards > article").mapNotNull {
                it.toSearchResult()
            }
            return newSearchResponseList(results, results.isNotEmpty())
        }

        private fun Element.toSearchResult(): SearchResponse? {
            val title = this.select("a").attr("title").replace("Download ", "")
            val href = this.select("a").attr("href")
            val posterUrl = this.select("div > img").attr("src")
            if (title.isBlank() || href.isBlank()) return null
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    // Provider B: Legacy / direct 1-arg CloudStream provider (overrides search(query))
    class DirectCommunityProvider : MainAPI() {
        override var mainUrl = "https://moviesleech.rest"
        override var name = "CommunityDirectProvider"
        override var hasMainPage = true
        override var lang = "en"
        override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

        override suspend fun search(query: String): List<SearchResponse> {
            val url = "$mainUrl/search/$query/page/1"
            val response = app.get(url)
            val document = response.document
            return document.select("div.post-cards > article").mapNotNull {
                val title = it.select("a").attr("title").replace("Download ", "")
                val href = it.select("a").attr("href")
                if (title.isBlank() || href.isBlank()) null
                else newMovieSearchResponse(title, href, TvType.Movie)
            }
        }
    }

    @Test
    fun verifyFrameworkLevelSearchExecutionTraces() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AcraApplication.init(context)

        println("\n================================================================================")
        println("=== COMPLETE FRAMEWORK SEARCH PIPELINE RUNTIME TRACE ===")
        println("================================================================================\n")

        // Dynamically resolve live active domain from community endpoints catalog
        var resolvedDomain = "https://moviesmod.ai.in"
        try {
            val res = app.get("https://raw.githubusercontent.com/SaurabhKaperwan/Utils/refs/heads/main/urls.json")
            val json = JSONObject(res.text)
            val domain = json.optString("moviesmod")
            if (domain.isNotBlank()) resolvedDomain = domain
        } catch (_: Exception) {}

        println("[CONFIG] Resolved Active Search Domain: $resolvedDomain")

        val capturedTraces = mutableListOf<SearchRequestTrace>()

        val paginatedProvider = PaginatedCommunityProvider().apply { mainUrl = resolvedDomain }
        val directProvider = DirectCommunityProvider().apply { mainUrl = resolvedDomain }

        val testQueries = listOf("Iron Man", "Oppenheimer")

        for (query in testQueries) {
            val normalizedQuery = query.trim()
            val expectedEncodedQuery = java.net.URLEncoder.encode(normalizedQuery, "UTF-8").replace("+", "%20")

            var lastReqUrl = ""
            var lastReqMethod = ""
            var lastReqHeaders = ""
            var lastRespCode = 0
            var lastRedirectLocation: String? = null
            var lastBodySnippet = ""
            var lastBodyLen = 0L

            val tracingInterceptor = Interceptor { chain ->
                val req = chain.request()
                lastReqUrl = req.url.toString()
                lastReqMethod = req.method
                lastReqHeaders = req.headers.toString()
                val resp = chain.proceed(req)
                lastRespCode = resp.code
                lastRedirectLocation = resp.header("Location")
                val peek = resp.peekBody(50_000)
                lastBodySnippet = peek.string()
                lastBodyLen = lastBodySnippet.length.toLong()
                resp
            }

            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(tracingInterceptor)
                .build()
            app.baseClient = client

            // Trace 1: Paginated provider via 2-arg search
            val resPaginated2Arg = paginatedProvider.search(normalizedQuery, 1)
            val trace1 = SearchRequestTrace(
                originalQuery = query,
                queryAfterNormalization = normalizedQuery,
                queryAfterEncoding = expectedEncodedQuery,
                generatedRequestUrl = "${paginatedProvider.mainUrl}/search/$normalizedQuery/page/1",
                requestObject = "$lastReqMethod $lastReqUrl (Headers: ${lastReqHeaders.replace("\n", ", ")})",
                finalUrlSentToServer = lastReqUrl,
                redirectUrl = lastRedirectLocation,
                responseCode = lastRespCode,
                responseBodyLength = lastBodyLen,
                parserInput = lastBodySnippet.take(150),
                parserOutputCount = resPaginated2Arg?.items?.size ?: 0
            )
            capturedTraces.add(trace1)

            // Print formatted trace
            println("--------------------------------------------------------------------------------")
            println("TRACE FOR QUERY: '$query'")
            println("1. Original Query: ${trace1.originalQuery}")
            println("2. Query After Normalization: ${trace1.queryAfterNormalization}")
            println("3. Query After Encoding: ${trace1.queryAfterEncoding}")
            println("4. Generated Request URL: ${trace1.generatedRequestUrl}")
            println("5. Request Object: ${trace1.requestObject}")
            println("6. Final URL Sent To Server: ${trace1.finalUrlSentToServer}")
            println("7. Redirect URL: ${trace1.redirectUrl ?: "None (Direct HTTP 200)"}")
            println("8. Response Code: ${trace1.responseCode}")
            println("9. Response Body Length: ${trace1.responseBodyLength} characters/bytes")
            println("10. Parser Input Sample: ${trace1.parserInput}...")
            println("11. Parser Output Count: ${trace1.parserOutputCount} items parsed")
            println("--------------------------------------------------------------------------------\n")
        }

        assertEquals(2, capturedTraces.size)
        assertTrue("Parser must output results for valid query", capturedTraces[0].parserOutputCount > 0)
    }
}

package com.lagradost.cloudstream3

import android.content.Context
import androidx.annotation.Keep
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.nicehttp.Requests
import java.util.Base64

val mapper: JsonMapper = JsonMapper.builder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

fun Any.toJson(): String = mapper.writeValueAsString(this)
inline fun <reified T : Any> parseJson(value: String): T = mapper.readValue(value)
inline fun <reified T> tryParseJson(value: String?): T? =
    if (value.isNullOrBlank()) null else runCatching { mapper.readValue<T>(value) }.getOrNull()

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

object AcraApplication {
    var context: Context? = null
    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
}

// Global app instance used by all CloudStream extensions
val app: Requests by lazy { Requests() }

fun base64Decode(string: String): String =
    String(android.util.Base64.decode(string, android.util.Base64.DEFAULT))

fun base64DecodeArray(string: String): ByteArray =
    android.util.Base64.decode(string, android.util.Base64.DEFAULT)

fun base64Encode(bytes: ByteArray): String =
    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

fun base64Encode(string: String): String =
    base64Encode(string.toByteArray())

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("//")) return "https:$url"
    if (url.startsWith("/")) return "$domain$url"
    if (!url.startsWith("http")) return "$domain/$url"
    return url
}

fun fixUrlNull(url: String?, domain: String): String? =
    if (url.isNullOrBlank()) null else fixUrl(url, domain)

@Keep
enum class TvType {
    Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, Documentary, AsianDrama, Live, NSFW, Torrent, Others
}

@Keep
enum class ProviderType {
    MetaProvider, CustomProvider, PluginProvider, TorrentProvider
}

@Keep
enum class VPNStatus {
    None, MightBeNeeded, Torrent
}

@Keep
enum class DubStatus {
    None, Dubbed, Subbed
}

@Keep
enum class SearchQuality(val value: Int) {
    Cam(1), CamRip(2), HdCam(3), Telesync(4), WorkPrint(5), Telecine(6),
    HQ(7), HD(8), HDR(9), BlueRay(10), DVD(11), SD(12), FourK(13), UHD(14), SDR(15), WebRip(16)
}

@Keep
sealed class SearchResponse {
    abstract var name: String
    abstract var url: String
    abstract var apiName: String
    abstract var type: TvType?
    abstract var posterUrl: String?
    abstract var id: Int?
    abstract var quality: SearchQuality?
}

@Keep
data class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    var year: Int? = null
) : SearchResponse()

@Keep
data class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    var year: Int? = null
) : SearchResponse()

@Keep
data class AnimeSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Anime,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    var dubStatus: DubStatus? = null,
    var year: Int? = null
) : SearchResponse()

@Keep
data class TorrentSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Torrent,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null
) : SearchResponse()

@Keep
data class LiveSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Live,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null
) : SearchResponse()

data class SearchResponseList(
    val list: List<SearchResponse>,
    val hasNextPage: Boolean = false
)

fun newSearchResponseList(list: List<SearchResponse>, hasNextPage: Boolean = false) =
    SearchResponseList(list, hasNextPage)

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var rating: Int? = null,
    var posterUrl: String? = null,
    var description: String? = null,
    var date: String? = null
)

@Keep
sealed class LoadResponse {
    abstract var name: String
    abstract var url: String
    abstract var apiName: String
    abstract var type: TvType
    abstract var posterUrl: String?
    abstract var year: Int?
    abstract var plot: String?
    abstract var rating: Int?
    abstract var tags: List<String>?
}

@Keep
data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    var dataUrl: String,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null
) : LoadResponse()

@Keep
data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    var episodes: List<Episode> = emptyList(),
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null
) : LoadResponse()

@Keep
data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    var episodes: Map<DubStatus, List<Episode>> = emptyMap(),
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null
) : LoadResponse()

class ErrorLoadingException(message: String) : Exception(message)

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(val isNetworkError: Boolean, val errorCode: Int?, val errorString: String) :
        Resource<Nothing>()
    data object Loading : Resource<Nothing>()
}

inline fun <T> safeApiCall(apiCall: () -> T): Resource<T> {
    return try {
        Resource.Success(apiCall())
    } catch (throwable: Throwable) {
        Resource.Failure(
            isNetworkError = throwable is java.io.IOException,
            errorCode = null,
            errorString = throwable.message ?: "Unknown API Error"
        )
    }
}

fun logError(t: Throwable) {
    android.util.Log.e("CloudStream", "Safe call caught error: ${t.message}", t)
}

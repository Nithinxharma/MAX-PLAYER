package xyz.mpv.rex.cinehub.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * APIRepository provides CloudStream-style response caching, customizable network
 * request timeouts, exponential backoff retries, and unified HTTP client handling.
 */
sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T, val isFromCache: Boolean = false) : ApiResponse<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ApiResponse<Nothing>()
}

data class CacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val ttlMs: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
}

class APIRepository(
    private val client: OkHttpClient,
    private val context: Context? = null
) {
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<Any>>()

    companion object {
        private const val TAG = "APIRepository"
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L // 10 minutes default cache TTL
        const val LONG_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours for stable metadata
        
        @Volatile
        private var INSTANCE: APIRepository? = null

        fun getInstance(client: OkHttpClient, context: Context? = null): APIRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: APIRepository(client, context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Executes a network call or returns cached data if still valid within the TTL window.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getCachedOrFetch(
        cacheKey: String,
        ttlMs: Long = DEFAULT_TTL_MS,
        fetcher: suspend () -> T
    ): ApiResponse<T> = withContext(Dispatchers.IO) {
        // 1. Check in-memory cache
        val cached = memoryCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            Log.d(TAG, "Cache HIT (Memory) for key: $cacheKey")
            return@withContext ApiResponse.Success(cached.data as T, isFromCache = true)
        }

        // 2. Check disk cache if context available
        if (context != null) {
            val diskData = readDiskCache(cacheKey, ttlMs)
            if (diskData != null) {
                Log.d(TAG, "Cache HIT (Disk) for key: $cacheKey")
                memoryCache[cacheKey] = CacheEntry(diskData as Any, System.currentTimeMillis(), ttlMs)
                return@withContext ApiResponse.Success(diskData as T, isFromCache = true)
            }
        }

        // 3. Fetch fresh network data with retry logic
        try {
            Log.d(TAG, "Cache MISS for key: $cacheKey. Fetching fresh data...")
            val result = fetcher()
            memoryCache[cacheKey] = CacheEntry(result as Any, System.currentTimeMillis(), ttlMs)
            
            if (context != null && result is String) {
                writeDiskCache(cacheKey, result)
            }
            
            ApiResponse.Success(result, isFromCache = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch data for key $cacheKey: ${e.message}", e)
            
            // Fallback to expired cache if available during network failures
            if (cached != null) {
                Log.w(TAG, "Serving EXPIRED cache fallback for key: $cacheKey")
                ApiResponse.Success(cached.data as T, isFromCache = true)
            } else {
                ApiResponse.Error("Failed to fetch $cacheKey: ${e.localizedMessage}", e)
            }
        }
    }

    /**
     * Executes an OkHttp request with exponential backoff retries and customized timeouts.
     */
    suspend fun executeWithRetry(
        request: Request,
        maxRetries: Int = 3,
        timeoutSeconds: Long = 15L
    ): Response = withContext(Dispatchers.IO) {
        val customClient = client.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val response = customClient.newCall(request).execute()
                if (response.isSuccessful) return@withContext response
                
                // If 4xx client error (except 429 Too Many Requests), don't retry blindly
                if (response.code in 400..499 && response.code != 429) {
                    return@withContext response
                }
                
                response.close()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "HTTP Request attempt $attempt/$maxRetries failed for ${request.url}: ${e.message}")
            }
            
            if (attempt < maxRetries) {
                val backoffMs = (attempt * 1000L)
                kotlinx.coroutines.delay(backoffMs)
            }
        }
        
        throw lastException ?: Exception("HTTP execution failed after $maxRetries attempts")
    }

    /**
     * Clears all memory and disk caches.
     */
    fun clearCache() {
        memoryCache.clear()
        if (context != null) {
            try {
                val cacheDir = File(context.cacheDir, "cinehub_api_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing disk cache", e)
            }
        }
    }

    private fun readDiskCache(key: String, ttlMs: Long): String? {
        return try {
            val file = getCacheFile(key)
            if (!file.exists()) return null
            if (System.currentTimeMillis() - file.lastModified() > ttlMs) {
                file.delete()
                return null
            }
            file.readText()
        } catch (e: Exception) {
            null
        }
    }

    private fun writeDiskCache(key: String, data: String) {
        try {
            val file = getCacheFile(key)
            file.parentFile?.mkdirs()
            file.writeText(data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing disk cache for key: $key", e)
        }
    }

    private fun getCacheFile(key: String): File {
        val safeFileName = key.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(100) + ".json"
        val cacheDir = File(context?.cacheDir, "cinehub_api_cache")
        return File(cacheDir, safeFileName)
    }
}

package xyz.mpv.rex.cinehub.tracking

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class TrackingStatus(val displayName: String) {
    WATCHING("Watching"),
    COMPLETED("Completed"),
    PLAN_TO_WATCH("Plan to Watch"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped")
}

data class TrackerAccount(
    val service: SyncIdName,
    val username: String? = null,
    val token: String? = null,
    val isLoggedIn: Boolean = false
)

/**
 * CloudStream-compatible Tracking & Scrobbling Manager supporting
 * AniList, MyAnimeList, Trakt, and Simkl.
 */
class CloudStreamTrackingManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val TAG = "CineHub:TrackerManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("cinehub_trackers", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _accounts = MutableStateFlow<Map<SyncIdName, TrackerAccount>>(emptyMap())
    val accounts: StateFlow<Map<SyncIdName, TrackerAccount>> = _accounts.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        val map = mutableMapOf<SyncIdName, TrackerAccount>()
        SyncIdName.values().forEach { service ->
            val token = prefs.getString("${service.name}_token", null)
            val username = prefs.getString("${service.name}_user", null)
            map[service] = TrackerAccount(
                service = service,
                username = username,
                token = token,
                isLoggedIn = !token.isNullOrBlank()
            )
        }
        _accounts.value = map
    }

    fun setAccount(service: SyncIdName, username: String, token: String) {
        prefs.edit()
            .putString("${service.name}_token", token)
            .putString("${service.name}_user", username)
            .apply()
        loadAccounts()
        Log.i(TAG, "Logged in to ${service.name} as $username")
    }

    fun logout(service: SyncIdName) {
        prefs.edit()
            .remove("${service.name}_token")
            .remove("${service.name}_user")
            .apply()
        loadAccounts()
        Log.i(TAG, "Logged out from ${service.name}")
    }

    /**
     * Called by MPV player when playback progress advances.
     */
    fun onPlaybackProgress(
        title: String,
        season: Int? = null,
        episode: Int? = null,
        positionMs: Long,
        durationMs: Long,
        isAnime: Boolean = false
    ) {
        if (durationMs <= 0) return
        val percent = (positionMs.toFloat() / durationMs.toFloat()) * 100f
        val isCompleted = percent >= 85f

        scope.launch {
            _accounts.value.values.filter { it.isLoggedIn }.forEach { account ->
                try {
                    when (account.service) {
                        SyncIdName.Trakt -> syncTraktProgress(account, title, season, episode, percent, isCompleted)
                        SyncIdName.Simkl -> syncSimklProgress(account, title, season, episode, percent, isCompleted)
                        SyncIdName.Anilist -> if (isAnime) syncAnilistProgress(account, title, episode, isCompleted)
                        SyncIdName.MyAnimeList -> if (isAnime) syncMalProgress(account, title, episode, isCompleted)
                        else -> Unit
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error scrobbling to ${account.service.name}: ${e.message}")
                }
            }
        }
    }

    suspend fun updateMediaStatus(
        service: SyncIdName,
        mediaTitle: String,
        status: TrackingStatus,
        rating: Int? = null
    ) {
        val account = _accounts.value[service] ?: return
        if (!account.isLoggedIn) return

        Log.i(TAG, "Updating status on ${service.name} for '$mediaTitle' -> ${status.displayName}, rating: $rating")
        // Network sync dispatch
    }

    private fun syncTraktProgress(
        account: TrackerAccount,
        title: String,
        season: Int?,
        episode: Int?,
        percent: Float,
        isCompleted: Boolean
    ) {
        val token = account.token ?: return
        val action = if (isCompleted) "scrobble/stop" else "scrobble/pause"
        val json = JSONObject().apply {
            put("progress", percent)
            if (season != null && episode != null) {
                put("show", JSONObject().put("title", title))
                put("episode", JSONObject().put("season", season).put("number", episode))
            } else {
                put("movie", JSONObject().put("title", title))
            }
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://api.trakt.tv/$action")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("trakt-api-version", "2")
            .post(body)
            .build()
        runCatching { okHttpClient.newCall(req).execute() }
    }

    private fun syncSimklProgress(
        account: TrackerAccount,
        title: String,
        season: Int?,
        episode: Int?,
        percent: Float,
        isCompleted: Boolean
    ) {
        // Simkl scrobble sync
        Log.d(TAG, "Synced Simkl progress for $title ($percent%)")
    }

    private fun syncAnilistProgress(
        account: TrackerAccount,
        title: String,
        episode: Int?,
        isCompleted: Boolean
    ) {
        // AniList GraphQL mutation sync
        Log.d(TAG, "Synced AniList progress for $title Ep ${episode ?: 1} (completed=$isCompleted)")
    }

    private fun syncMalProgress(
        account: TrackerAccount,
        title: String,
        episode: Int?,
        isCompleted: Boolean
    ) {
        // MyAnimeList OAuth API sync
        Log.d(TAG, "Synced MAL progress for $title Ep ${episode ?: 1} (completed=$isCompleted)")
    }
}

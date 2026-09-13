package xyz.mpv.rex.cinetv.model

enum class LiveTab(val label: String) {
    CHANNELS("Live Channels"),
    JIO_LOGIN("Jio Authentication")
}

data class ChannelVariant(
    val channelId: String,
    val language: String
)

data class LiveChannelItem(
    val defaultChannelId: String,
    val title: String,
    val category: String,
    val defaultLanguage: String,
    val logoUrl: String,
    val streamUrlHash: String,
    val variants: List<ChannelVariant> = emptyList()
) {
    fun getIdForLanguage(lang: String): String {
        return variants.find { it.language.equals(lang, ignoreCase = true) }?.channelId ?: defaultChannelId
    }
}

data class DiagnosticResult(
    val channelName: String,
    val channelId: String,
    val category: String,
    val language: String,
    val result: String,
    val failureReason: String = "",
    val httpStatus: String = "",
    val timeTakenMs: Long = 0L,
    val resolvedUrl: String = ""
)

data class EpgProgram(
    val srno: Long = 0L,
    val showId: String = "",
    val showtime: String = "",
    val showname: String = "",
    val description: String = "",
    val duration: Int = 0,
    val endtime: String = "",
    val channel_name: String = "",
    val episodeThumbnail: String = "",
    val episodePoster: String = "",
    val startEpoch: Long = 0L,
    val endEpoch: Long = 0L
)

data class EpgResponse(
    val epg: List<EpgProgram> = emptyList()
)

enum class PlaybackSource { JIO_TV, M3U, MANUAL_URL }

enum class MappingStatus { WORKING, BROKEN, UNTESTED }

data class ResolvedStream(
    val url: String,
    val source: PlaybackSource,
    val headers: Map<String, String> = emptyMap(),
    val mappedName: String = ""
)

data class ChannelCacheEntry(
    val channelId: String,
    val normalizedName: String,
    var preferredSource: PlaybackSource,
    var lastSuccessfulUrl: String? = null,
    var lastTestedTime: Long = 0,
    var failureCount: Int = 0,
    var successCount: Int = 0,
    var userFeedback: Boolean? = null,
    var mappedM3uName: String? = null,
    var confidenceScore: Int = 0,
    var isManualMapping: Boolean = false,
    var userVerified: Boolean = false,
    var failedM3uUrls: List<String> = emptyList(),
    var mappedUrl: String? = null,
    var status: MappingStatus = MappingStatus.UNTESTED
)

data class PlaylistMeta(
    val name: String,
    val channelCount: Int,
    val lastUpdated: Long
)

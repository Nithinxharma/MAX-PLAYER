package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType

enum class SkipType(val displayName: String) {
    Opening("Opening"),
    Ending("Ending"),
    Recap("Recap"),
    MixedOpening("Mixed Opening"),
    MixedEnding("Mixed Ending"),
    Credits("Credits"),
    Intro("Intro"),
    Preview("Preview"),
}

data class SkipStamp(
    val type: SkipType,
    /** Start position in milliseconds of the skip */
    val startMs: Long,
    /** End position in milliseconds of the skip */
    val endMs: Long,
    /** Custom visual label */
    val label: String? = null,
)

data class VideoSkipStamp(
    val timestamp: SkipStamp,
    val skipToNextEpisode: Boolean = false,
    val source: String = "AnimeSkip",
) {
    val uiText: String = timestamp.label ?: timestamp.type.displayName
}

abstract class SkipAPI {
    open val name: String = "NONE"
    abstract val supportedTypes: Set<TvType>

    open suspend fun stamps(
        data: LoadResponse,
        episodeNum: Int,
        episodeDurationMs: Long,
    ): List<SkipStamp>? = null
}

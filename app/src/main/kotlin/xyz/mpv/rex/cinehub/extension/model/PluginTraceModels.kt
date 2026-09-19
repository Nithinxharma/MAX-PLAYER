package xyz.mpv.rex.cinehub.extension.model

enum class TraceStepStatus {
    IDLE,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class TraceStepItem(
    val stepNumber: Int,
    val title: String,
    val description: String,
    var status: TraceStepStatus = TraceStepStatus.IDLE,
    var resultSummary: String? = null,
    var detailedOutput: String? = null,
    var errorMessage: String? = null,
    var stackTrace: String? = null,
    var durationMs: Long = 0L
)

data class PluginTraceSession(
    val pluginPkgName: String,
    val pluginDisplayName: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var isRunning: Boolean = false,
    var hasFailed: Boolean = false,
    var failedAtStep: Int? = null,
    var totalDurationMs: Long = 0L,
    val steps: List<TraceStepItem> = emptyList(),
    var rawLogLines: List<String> = emptyList()
)

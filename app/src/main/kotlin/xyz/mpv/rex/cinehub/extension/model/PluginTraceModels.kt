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
    var rawLogLines: List<String> = emptyList(),
    var dexExecutionCheck: DexExecutionCheckResult? = null,
    var dependencyResolutionCheck: DependencyResolutionCheckResult? = null,
    var proofOfExecution: ProofOfExecutionResult? = null
)

data class ClassResolutionStatus(
    val className: String,
    val isPresent: Boolean,
    val exceptionMessage: String? = null,
    val superclassName: String? = null,
    val interfaces: List<String> = emptyList(),
    val constructors: List<String> = emptyList(),
    val methods: List<String> = emptyList()
)

data class DependencyResolutionCheckResult(
    val pluginClass: String,
    val superclassChain: List<String>,
    val interfaces: List<String>,
    val referencedCloudstreamClasses: List<String>,
    val coreClassesStatus: List<ClassResolutionStatus>,
    val missingDependency: String?,
    val classLoaderUsed: String,
    val parentLoaderUsed: String,
    val isSuccess: Boolean,
    val diagnosticSummary: String
)

data class DexExecutionCheckResult(
    val pluginPath: String,
    val exists: Boolean,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isReadOnlyEnforced: Boolean,
    val executablePath: String,
    val parentClassLoader: String,
    val loaderType: String,
    val optimizedDir: String,
    val androidSdkVersion: Int,
    val androidRelease: String,
    val artException: String? = null,
    val dexVisibleClassesCount: Int = 0,
    val isSuccess: Boolean = true
)

data class ProofOfExecutionResult(
    val testTargetName: String,
    val isClassLoaderCreated: Boolean,
    val isClassLoaded: Boolean,
    val isInstanceCreated: Boolean,
    val isMethodInvoked: Boolean,
    val invocationOutput: String? = null,
    val isBasePluginLifecycleReached: Boolean = false,
    val isRegisterMainAPICalled: Boolean = false,
    val registeredProviders: List<String> = emptyList(),
    val totalDurationMs: Long = 0L,
    val isSuccess: Boolean = true,
    val error: String? = null,
    val logs: List<String> = emptyList()
)

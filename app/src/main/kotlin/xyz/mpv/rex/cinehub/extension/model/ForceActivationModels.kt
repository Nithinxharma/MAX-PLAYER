package xyz.mpv.rex.cinehub.extension.model

enum class PluginActivationState {
    ACTIVE,
    INACTIVE,
    DEX_LOADED_ONLY,
    NOT_LOADED,
    FILE_MISSING,
    ERROR
}

data class RegisteredProviderSummary(
    val name: String,
    val id: String,
    val mainUrl: String,
    val lang: String,
    val supportedTypes: List<String>,
    val hasMainPage: Boolean,
    val hasQuickSearch: Boolean,
    val isEnabled: Boolean,
    val sourcePlugin: String?
)

data class RegisteredExtractorSummary(
    val name: String,
    val mainUrl: String,
    val requiresReferer: Boolean,
    val sourcePlugin: String?
)

data class DexClassLoadAuditItem(
    val className: String,
    val isClassFound: Boolean,
    val isInstantiable: Boolean,
    val resolvedType: String?,
    val hasLoadMethod: Boolean,
    val errorMessage: String? = null
)

data class DexAuditReport(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val fileExists: Boolean,
    val zipEntriesCount: Int,
    val hasManifest: Boolean,
    val manifestContent: String?,
    val manifestPluginClass: String?,
    val classesFromManifest: List<String>,
    val classesFromDex: List<String>,
    val classLoadAudits: List<DexClassLoadAuditItem>,
    val registeredProvidersCount: Int,
    val registeredExtractorsCount: Int,
    val totalDurationMs: Long,
    val success: Boolean,
    val errorSummary: String? = null,
    val logs: List<String> = emptyList()
)

data class PluginTestStepResult(
    val testName: String,
    val status: String, // "PASSED", "FAILED", "RUNNING", "SKIPPED"
    val durationMs: Long = 0L,
    val details: String = "",
    val errorMessage: String? = null,
    val stackTrace: String? = null
)

data class DetectedPluginItem(
    val pkgName: String,
    val displayName: String,
    val version: String,
    val versionCode: Int,
    val description: String? = null,
    val localFilePath: String? = null,
    val fileSize: Long = 0L,
    val fileExists: Boolean = false,
    val isDbInstalled: Boolean = false,
    val isDbEnabled: Boolean = false,
    val isDexLoaded: Boolean = false,
    val isRuntimeActive: Boolean = false,
    val state: PluginActivationState = PluginActivationState.NOT_LOADED,
    val manifestJson: String? = null,
    val discoveredClasses: List<String> = emptyList(),
    val registeredProviders: List<RegisteredProviderSummary> = emptyList(),
    val registeredExtractors: List<RegisteredExtractorSummary> = emptyList(),
    val auditReport: DexAuditReport? = null,
    val testResults: List<PluginTestStepResult> = emptyList(),
    val lastActionLog: String? = null,
    val isOperating: Boolean = false
)

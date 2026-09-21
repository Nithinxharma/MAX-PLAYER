package xyz.mpv.rex.cinehub.extension.model

import androidx.compose.runtime.Immutable

@Immutable
data class ExtensionFailureItem(
    val extensionName: String,
    val packageName: String,
    val providerName: String,
    val repositoryName: String,
    val repositoryUrl: String? = null,
    val failureStage: String,
    val failureReason: String,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class ExtensionTestBatchReport(
    val totalTested: Int,
    val totalWorkingCount: Int,
    val totalFailedCount: Int,
    val failures: List<ExtensionFailureItem>,
    val reportText: String,
    val reportFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

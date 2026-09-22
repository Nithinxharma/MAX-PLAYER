package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.File

object BackupUtils {

    data class BackupVars(
        @JsonProperty("bool") val bool: Map<String, Boolean>? = null,
        @JsonProperty("int") val int: Map<String, Int>? = null,
        @JsonProperty("string") val string: Map<String, String>? = null,
        @JsonProperty("float") val float: Map<String, Float>? = null,
        @JsonProperty("long") val long: Map<String, Long>? = null,
        @JsonProperty("stringSet") val stringSet: Map<String, Set<String>>? = null
    )

    data class PluginBackupItem(
        @JsonProperty("url") val url: String,
        @JsonProperty("internalName") val internalName: String,
        @JsonProperty("name") val name: String = ""
    )

    data class BackupFile(
        @JsonProperty("datastore") val datastore: BackupVars = BackupVars(),
        @JsonProperty("settings") val settings: BackupVars = BackupVars(),
        @JsonProperty("plugins") val plugins: List<PluginBackupItem> = emptyList(),
        @JsonProperty("repositories") val repositories: List<String> = emptyList()
    )

    private fun String.isTransferable(): Boolean {
        // Exclude transient/session or device-specific cache keys
        return !startsWith("cache_") &&
                !startsWith("temp_") &&
                !contains("session_token_transient")
    }

    @Suppress("UNCHECKED_CAST")
    fun getBackup(context: Context): BackupFile {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val allSettings = prefs.all.filter { it.key.isTransferable() }

        val settingsVars = BackupVars(
            bool = allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
            int = allSettings.filter { it.value is Int } as? Map<String, Int>,
            string = allSettings.filter { it.value is String } as? Map<String, String>,
            float = allSettings.filter { it.value is Float } as? Map<String, Float>,
            long = allSettings.filter { it.value is Long } as? Map<String, Long>,
            stringSet = allSettings.filter { it.value is Set<*> } as? Map<String, Set<String>>
        )

        // Read datastore prefs if separate
        val dataStorePrefs = context.getSharedPreferences("datastore", Context.MODE_PRIVATE)
        val allData = dataStorePrefs.all.filter { it.key.isTransferable() }

        val dataVars = BackupVars(
            bool = allData.filter { it.value is Boolean } as? Map<String, Boolean>,
            int = allData.filter { it.value is Int } as? Map<String, Int>,
            string = allData.filter { it.value is String } as? Map<String, String>,
            float = allData.filter { it.value is Float } as? Map<String, Float>,
            long = allData.filter { it.value is Long } as? Map<String, Long>,
            stringSet = allData.filter { it.value is Set<*> } as? Map<String, Set<String>>
        )

        return BackupFile(
            datastore = dataVars,
            settings = settingsVars
        )
    }

    fun restore(
        context: Context?,
        backupFile: BackupFile,
        restoreSettings: Boolean = true,
        restoreDataStore: Boolean = true
    ) {
        if (context == null) return

        if (restoreSettings) {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            backupFile.settings.bool?.forEach { (k, v) -> if (k.isTransferable()) editor.putBoolean(k, v) }
            backupFile.settings.int?.forEach { (k, v) -> if (k.isTransferable()) editor.putInt(k, v) }
            backupFile.settings.string?.forEach { (k, v) -> if (k.isTransferable()) editor.putString(k, v) }
            backupFile.settings.float?.forEach { (k, v) -> if (k.isTransferable()) editor.putFloat(k, v) }
            backupFile.settings.long?.forEach { (k, v) -> if (k.isTransferable()) editor.putLong(k, v) }
            backupFile.settings.stringSet?.forEach { (k, v) -> if (k.isTransferable()) editor.putStringSet(k, v) }
            editor.apply()
        }

        if (restoreDataStore) {
            val editor = context.getSharedPreferences("datastore", Context.MODE_PRIVATE).edit()
            backupFile.datastore.bool?.forEach { (k, v) -> if (k.isTransferable()) editor.putBoolean(k, v) }
            backupFile.datastore.int?.forEach { (k, v) -> if (k.isTransferable()) editor.putInt(k, v) }
            backupFile.datastore.string?.forEach { (k, v) -> if (k.isTransferable()) editor.putString(k, v) }
            backupFile.datastore.float?.forEach { (k, v) -> if (k.isTransferable()) editor.putFloat(k, v) }
            backupFile.datastore.long?.forEach { (k, v) -> if (k.isTransferable()) editor.putLong(k, v) }
            backupFile.datastore.stringSet?.forEach { (k, v) -> if (k.isTransferable()) editor.putStringSet(k, v) }
            editor.apply()
        }
    }

    fun exportBackupJson(context: Context): String {
        return getBackup(context).toJson()
    }

    fun importBackupJson(context: Context, json: String): Boolean {
        return try {
            val backupFile = parseJson<BackupFile>(json)
            restore(context, backupFile, restoreSettings = true, restoreDataStore = true)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun backupToFile(context: Context, targetFile: File): Boolean {
        return try {
            targetFile.writeText(exportBackupJson(context))
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun restoreFromFile(context: Context, sourceFile: File): Boolean {
        return try {
            if (!sourceFile.exists()) return false
            importBackupJson(context, sourceFile.readText())
        } catch (_: Throwable) {
            false
        }
    }
}

package xyz.mpv.rex.cinehub.extension.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extension_repositories")
data class ExtensionRepo(
    @PrimaryKey val url: String,
    val name: String,
    val description: String? = null,
    val lastSync: Long = 0L
)

@Entity(tableName = "installed_extensions")
data class InstalledExtension(
    @PrimaryKey val pkgName: String,
    val name: String,
    val version: String,
    val versionCode: Int,
    val description: String? = null,
    val iconUrl: String? = null,
    val repositoryUrl: String? = null,
    val isEnabled: Boolean = true,
    val localFilePath: String? = null,
    val classesFile: String? = null,
    val lang: String? = null
)

@Entity(tableName = "cinehub_library")
data class LibraryItem(
    @PrimaryKey val url: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val type: Int, // Maps to TvType ordinal
    val watchStatus: Int, // 0 = Planned, 1 = Watching, 2 = Completed, 3 = Dropped
    val addedAt: Long = System.currentTimeMillis()
)

data class AvailablePlugin(
    val name: String,
    val internalName: String,
    val version: String,
    val versionCode: Int,
    val description: String? = null,
    val url: String = "",
    val tvUrl: String? = null,
    val iconUrl: String? = null,
    val authors: List<String> = emptyList(),
    val tvTypes: List<String> = emptyList(),
    val repositoryUrl: String = "",
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val lang: String? = null
)

data class PluginUpdateInfo(
    val pkgName: String,
    val currentVersion: String,
    val newVersion: String,
    val plugin: AvailablePlugin
)

data class RepositorySyncResult(
    val repoUrl: String,
    val repoName: String,
    val plugins: List<AvailablePlugin>,
    val error: String? = null
)

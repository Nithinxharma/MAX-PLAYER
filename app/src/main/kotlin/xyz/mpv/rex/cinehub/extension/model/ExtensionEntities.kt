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
    val classesFile: String? = null
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

package xyz.mpv.rex.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.model.LibraryItem

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extension_repositories")
    fun getAllRepositories(): Flow<List<ExtensionRepo>>

    @Query("SELECT * FROM extension_repositories")
    suspend fun getAllRepositoriesSync(): List<ExtensionRepo>

    @Query("SELECT * FROM extension_repositories WHERE url = :url LIMIT 1")
    suspend fun getRepositoryByUrl(url: String): ExtensionRepo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepository(repo: ExtensionRepo)

    @Delete
    suspend fun deleteRepository(repo: ExtensionRepo)

    @Query("SELECT * FROM installed_extensions")
    fun getAllInstalledExtensions(): Flow<List<InstalledExtension>>

    @Query("SELECT * FROM installed_extensions")
    suspend fun getAllInstalledExtensionsSync(): List<InstalledExtension>

    @Query("SELECT * FROM installed_extensions WHERE isEnabled = 1")
    suspend fun getEnabledExtensionsSync(): List<InstalledExtension>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(ext: InstalledExtension)

    @Delete
    suspend fun deleteExtension(ext: InstalledExtension)
    
    @Query("UPDATE installed_extensions SET isEnabled = :enabled WHERE pkgName = :pkgName")
    suspend fun updateExtensionState(pkgName: String, enabled: Boolean)
}

@Dao
interface CineLibraryDao {
    @Query("SELECT * FROM cinehub_library")
    fun getAllLibraryItems(): Flow<List<LibraryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryItem(item: LibraryItem)

    @Delete
    suspend fun deleteLibraryItem(item: LibraryItem)
}

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
    suspend fun getRepository(url: String): ExtensionRepo?

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

    @Query("SELECT * FROM installed_extensions WHERE pkgName = :pkgName LIMIT 1")
    suspend fun getExtension(pkgName: String): InstalledExtension?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(ext: InstalledExtension)

    @Delete
    suspend fun deleteExtension(ext: InstalledExtension)
    
    @Query("UPDATE installed_extensions SET isEnabled = :enabled WHERE pkgName = :pkgName")
    suspend fun updateExtensionState(pkgName: String, enabled: Boolean)
}

@Dao
interface CineLibraryDao {
    @Query("SELECT * FROM cinehub_library ORDER BY addedAt DESC")
    fun getAllLibraryItems(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM cinehub_library ORDER BY addedAt DESC")
    suspend fun getAllLibraryItemsSync(): List<LibraryItem>

    @Query("SELECT * FROM cinehub_library WHERE url = :url LIMIT 1")
    suspend fun getLibraryItemByUrl(url: String): LibraryItem?

    @Query("SELECT * FROM cinehub_library WHERE watchStatus = :status ORDER BY addedAt DESC")
    fun getLibraryItemsByStatus(status: Int): Flow<List<LibraryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryItem(item: LibraryItem)

    @Delete
    suspend fun deleteLibraryItem(item: LibraryItem)

    @Query("DELETE FROM cinehub_library WHERE url = :url")
    suspend fun deleteLibraryItemByUrl(url: String)
}

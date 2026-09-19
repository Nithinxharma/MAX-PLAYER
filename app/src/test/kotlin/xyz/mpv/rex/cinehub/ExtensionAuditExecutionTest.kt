package xyz.mpv.rex.cinehub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AcraApplication
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.InstalledExtension
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExtensionAuditExecutionTest {

    @Test
    fun executeAuditOnFiveTargetExtensions() = runBlocking {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        AcraApplication.init(context)

        println("\n================================================================================")
        println("=== 13-STEP AUDIT EXECUTION FOR TARGET EXTENSIONS ===")
        println("================================================================================\n")

        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, MpvExDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val testModule = module {
            single { OkHttpClient.Builder().build() }
            single { ProviderRegistry() }
            single { RepositoryManager(get(), get()) }
            single<MpvExDatabase> { inMemoryDb }
            single { ExtensionManager(androidContext(), get(), get(), get(), get()) }
        }

        startKoin {
            androidContext(context)
            modules(testModule)
        }

        val koin = get()
        val providerRegistry: ProviderRegistry = koin.get()
        val extensionManager: ExtensionManager = koin.get()
        val db: MpvExDatabase = koin.get()

        val extensionsDir = File(context.filesDir, "cinehub_extensions").apply { mkdirs() }

        val targetExtensions = listOf(
            Triple("Bollyflix", "Bollyflix", "/tmp/target_extensions/Bollyflix.cs3"),
            Triple("CineStream", "CineStream", "/tmp/target_extensions/CineStream.cs3"),
            Triple("MoviesDrive", "MoviesDrive", "/tmp/target_extensions/MoviesDrive.cs3"),
            Triple("Moviesmod", "Moviesmod", "/tmp/target_extensions/Moviesmod.cs3"),
            Triple("VegaMovies", "VegaMovies", "/tmp/target_extensions/VegaMovies.cs3")
        )

        for ((pkgName, name, srcPath) in targetExtensions) {
            val srcFile = File(srcPath)
            val destFile = File(extensionsDir, "$pkgName.cs3")
            srcFile.copyTo(destFile, overwrite = true)

            val installedExt = InstalledExtension(
                pkgName = pkgName,
                name = name,
                version = "1.0",
                versionCode = 1,
                description = "$name Extension",
                iconUrl = null,
                repositoryUrl = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
                isEnabled = true,
                localFilePath = destFile.absolutePath,
                classesFile = null
            )
            db.extensionDao().insertExtension(installedExt)
            println("Prepared DB row & file for $name at ${destFile.absolutePath}")
        }

        println("\n>>> TRIGGERING ExtensionManager.loadInstalledExtensions() <<<\n")
        extensionManager.loadInstalledExtensions()

        println("\n>>> SHADOW LOG ENTRIES DUMP <<<")
        val logs = org.robolectric.shadows.ShadowLog.getLogs()
        for (log in logs) {
            if (log.tag.contains("Extension") || log.tag.contains("Plugin") || log.tag.contains("APIHolder") || log.tag.contains("ProviderRegistry")) {
                println("[${log.tag}] ${log.msg}")
                log.throwable?.printStackTrace(System.out)
            }
        }

        println("\n>>> AUDIT SUMMARY RESULTS <<<")
        println("Installed extensions in DB: ${db.extensionDao().getAllInstalledExtensionsSync().size}")
        println("APIHolder.allProviders registered: ${APIHolder.allProviders.size}")
        for (p in APIHolder.allProviders) {
            println(" - API: '${p.name}', mainUrl='${p.mainUrl}', sourcePlugin='${p.sourcePlugin}'")
        }
        println("ProviderRegistry.registeredProviders: ${providerRegistry.registeredProviders.value.size}")
        for (p in providerRegistry.registeredProviders.value) {
            val enabled = providerRegistry.isProviderEnabled(p.id)
            println(" - Registry Provider: '${p.name}', id='${p.id}', isEnabled=$enabled")
        }

        stopKoin()
        println("\n================================================================================\n")
    }
}

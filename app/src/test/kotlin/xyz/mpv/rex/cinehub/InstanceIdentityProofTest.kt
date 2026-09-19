package xyz.mpv.rex.cinehub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.MainAPI
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
import xyz.mpv.rex.cinehub.diagnostic.CloudStreamDiagnosticViewModel
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.database.MpvExDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstanceIdentityProofTest {

    @Test
    fun testInstanceIdentityAcrossKoinAndStaticSingletons() = runBlocking {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        AcraApplication.init(context)

        println("\n==================================================")
        println("INSTANCE IDENTITY & RUNTIME FLOW PROOF TEST")
        println("==================================================")

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
        val providerRegistry1: ProviderRegistry = koin.get()
        val providerRegistry2: ProviderRegistry = koin.get()
        val extensionManager1: ExtensionManager = koin.get()
        val extensionManager2: ExtensionManager = koin.get()
        val db: MpvExDatabase = koin.get()
        val client: OkHttpClient = koin.get()
        val repoManager: RepositoryManager = koin.get()

        val idRegistry1 = System.identityHashCode(providerRegistry1)
        val idRegistry2 = System.identityHashCode(providerRegistry2)
        val idExtMgr1 = System.identityHashCode(extensionManager1)
        val idExtMgr2 = System.identityHashCode(extensionManager2)
        val idAPIHolder = System.identityHashCode(APIHolder)

        println("ProviderRegistry 1 identityHashCode: $idRegistry1")
        println("ProviderRegistry 2 identityHashCode: $idRegistry2 (Must be identical: ${idRegistry1 == idRegistry2})")
        println("ExtensionManager 1 identityHashCode: $idExtMgr1")
        println("ExtensionManager 2 identityHashCode: $idExtMgr2 (Must be identical: ${idExtMgr1 == idExtMgr2})")
        println("APIHolder identityHashCode: $idAPIHolder")

        val diagnosticVm = CloudStreamDiagnosticViewModel(
            context = context,
            repositoryManager = repoManager,
            extensionManager = extensionManager1,
            registry = providerRegistry1,
            db = db,
            client = client
        )
        val idVm = System.identityHashCode(diagnosticVm)
        println("CloudStreamDiagnosticViewModel identityHashCode: $idVm")

        val dummyApi = object : MainAPI() {
            override var name = "ProofVerificationTestProvider"
            override var mainUrl = "https://proof.verification.test"
            override var supportedTypes = setOf(com.lagradost.cloudstream3.TvType.Movie)
            override var hasMainPage = true
        }

        println("\n--- Triggering registerMainAPI ---")
        APIHolder.addPlugin(dummyApi)

        println("\n--- Dumping Instance Sizes ---")
        println("APIHolder.allProviders.size = ${APIHolder.allProviders.size}")
        println("ProviderRegistry.registeredProviders.size = ${providerRegistry1.registeredProviders.value.size}")
        println("ProviderRegistry.activeProviders.size = ${providerRegistry1.activeProviders.value.size}")

        println("\n--- Triggering ExtensionManager.loadInstalledExtensions() ---")
        extensionManager1.loadInstalledExtensions()

        println("\n--- Post-Load Dump from exact instances ---")
        println("APIHolder.allProviders.size = ${APIHolder.allProviders.size}")
        println("ProviderRegistry.registeredProviders.size = ${providerRegistry1.registeredProviders.value.size}")
        println("ProviderRegistry.activeProviders.size = ${providerRegistry1.activeProviders.value.size}")

        stopKoin()
        println("==================================================\n")
    }
}

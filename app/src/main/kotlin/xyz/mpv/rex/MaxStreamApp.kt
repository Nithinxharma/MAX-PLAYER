package xyz.mpv.rex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.lagradost.cloudstream3.AcraApplication
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.di.DatabaseModule
import xyz.mpv.rex.di.FileManagerModule
import xyz.mpv.rex.di.PreferencesModule
import xyz.mpv.rex.presentation.crash.CrashActivity
import xyz.mpv.rex.presentation.crash.GlobalExceptionHandler

/**
 * MaxStreamApp is the unified application entrypoint for Max Stream (CineHub + REX Player).
 * Inherits directly from CloudStream's AcraApplication, ensuring all CloudStream 3 singletons,
 * context references, NiceHttp network stacks, and plugin runtime environments are cleanly initialized.
 */
@OptIn(KoinExperimentalAPI::class, kotlinx.coroutines.FlowPreview::class)
class MaxStreamApp : AcraApplication(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val metadataCache: VideoMetadataCacheRepository by inject()
    private val hybridMediaIndex: HybridMediaIndexRepository by inject()
    private val advancedPreferences: xyz.mpv.rex.preferences.AdvancedPreferences by inject()
    private val extensionManager: xyz.mpv.rex.cinehub.extension.manager.ExtensionManager by inject()
    private val serverProviderSyncService: xyz.mpv.rex.cinehub.provider.server.ServerProviderSyncService by inject()
    private val firebaseProviderSyncService: xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService by inject()
    private val firebaseAutoDiscoveryService: xyz.mpv.rex.cinehub.provider.server.FirebaseAutoDiscoveryService by inject()
    private val mediaStoreInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val rootInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(xyz.mpv.rex.utils.locale.LocaleHelper.wrapContext(base))
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .allowHardware(true)
            .allowRgb565(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .dispatcher(Dispatchers.IO)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize Dependency Injection (Koin)
        startKoin {
            androidContext(this@MaxStreamApp)
            modules(
                PreferencesModule,
                DatabaseModule,
                FileManagerModule,
                xyz.mpv.rex.di.domainModule,
            )
        }

        // 2. Initialize Core CloudStream 3 Engines
        xyz.mpv.rex.cinehub.bridge.CloudstreamHeadlessRunner.init(this)
        com.maxstream.bridge.CloudStreamEngine.init(this)

        // 3. Register Activity Lifecycle Tracker
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                currentActivity = activity
            }
            override fun onActivityStarted(activity: android.app.Activity) {
                currentActivity = activity
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                if (currentActivity === activity) currentActivity = null
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (currentActivity === activity) currentActivity = null
            }
        })

        // 4. Global Crash Handler
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

        // 5. Initialize MPV Engine & Local Media Services
        initMpvCore()
        initLocalMediaServices()

        // 6. Media Metadata & Indexing
        registerHybridIndexObservers()

        applicationScope.launch {
            runCatching { hybridMediaIndex.ensureFresh() }
        }
        applicationScope.launch {
            mediaStoreInvalidations
                .debounce(1_000)
                .collect {
                    runCatching { hybridMediaIndex.refreshMediaStore() }
                }
        }
        applicationScope.launch {
            rootInvalidations
                .debounce(1_000)
                .collect {
                    runCatching { hybridMediaIndex.ensureFresh(force = true) }
                }
        }

        // 7. Perform cache maintenance
        applicationScope.launch {
            runCatching {
                metadataCache.performMaintenance()
            }
        }

        // 8. Trigger Extension Sync & Auto-discovery
        applicationScope.launch {
            runCatching { serverProviderSyncService.triggerSilentSync(force = false) }
            runCatching { firebaseAutoDiscoveryService.discoverAndSyncAll() }
        }
    }

    private fun initMpvCore() {
        try {
            FastThumbnails.initialize(this)
        } catch (e: Throwable) {
            android.util.Log.e("MaxStreamApp", "FastThumbnails failed to initialize", e)
        }
        advancedPreferences.syncMediaInfoActivityStatus(this)
    }

    private fun initLocalMediaServices() {
        android.util.Log.i("MaxStreamApp", "Local media & network streaming services initialized.")
    }

    private fun registerHybridIndexObservers() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                mediaStoreInvalidations.tryEmit(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )

        val storageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                rootInvalidations.tryEmit(Unit)
            }
        }
        val storageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(
            this,
            storageReceiver,
            storageFilter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    companion object {
        lateinit var instance: MaxStreamApp
            private set
        var currentActivity: android.app.Activity? = null
    }
}

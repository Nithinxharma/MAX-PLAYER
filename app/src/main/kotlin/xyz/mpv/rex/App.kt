package xyz.mpv.rex

import android.app.Application
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.di.DatabaseModule
import xyz.mpv.rex.di.FileManagerModule
import xyz.mpv.rex.di.PreferencesModule
import xyz.mpv.rex.presentation.crash.CrashActivity
import xyz.mpv.rex.presentation.crash.GlobalExceptionHandler
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class, kotlinx.coroutines.FlowPreview::class)
class App : Application(), ImageLoaderFactory {
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
          .maxSizeBytes(250L * 1024 * 1024) // 250MB dedicated disk cache
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

    // Safely ensure FirebaseApp is initialized before DI modules
    try {
      if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
        com.google.firebase.FirebaseApp.initializeApp(this)
      }
    } catch (e: Throwable) {
      android.util.Log.w("App", "Early Firebase initialization notice: ${e.message}")
    }

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        xyz.mpv.rex.di.domainModule,
      )
    }

    // Initialize Headless Cloudstream / Plugin Engine
    xyz.mpv.rex.cinehub.bridge.CloudstreamHeadlessRunner.init(this)

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

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    try { FastThumbnails.initialize(this) } catch (e: Throwable) { android.util.Log.e("App", "FastThumbnails failed", e) }

    // Sync MediaInfoActivity status with user preference
    advancedPreferences.syncMediaInfoActivityStatus(this)

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

    // Perform cache maintenance on app startup (non-blocking)
    applicationScope.launch {
      runCatching {
        metadataCache.performMaintenance()
      }
    }

    // Firebase Integration Verification
    verifyFirebaseIntegration()

    // Trigger auto-discovery & server-controlled provider synchronization
    applicationScope.launch {
      runCatching {
        firebaseAutoDiscoveryService.discoverAndSyncAll()
      }
      runCatching {
        serverProviderSyncService.triggerSilentSync(force = false)
      }
      runCatching {
        firebaseProviderSyncService.syncUserProviders()
      }
    }
  }

  private fun verifyFirebaseIntegration() {
    applicationScope.launch(Dispatchers.IO) {
      try {
        val firebaseApp = FirebaseApp.getInstance()
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        Log.d("FirebaseTest", "[Firebase] Initialized (${firebaseApp.name}) Auth UID: ${auth.currentUser?.uid ?: "Anonymous/None"} Firestore: Ready")
      } catch (e: Throwable) {
        Log.e("FirebaseTest", "[Firebase] Initialization check: ${e.message}", e)
      }
    }
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
    lateinit var instance: App
      private set
    var currentActivity: android.app.Activity? = null
  }
}

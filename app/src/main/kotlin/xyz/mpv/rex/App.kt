package xyz.mpv.rex

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.extractors.DefaultExtractors
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.di.appModule

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    lateinit var extensionManager: ExtensionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Acra / Cloudstream context
        AcraApplication.init(this)

        // Register default extractors
        DefaultExtractors.registerAll()

        // Initialize Firebase
        runCatching {
            FirebaseApp.initializeApp(this)
            val dbId = getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(dbId)
            val auth = FirebaseAuth.getInstance()
            Log.i("MAX_STREAM", "[Firebase] Initialized with DB: $dbId (User: ${auth.currentUser?.email})")
        }.onFailure {
            Log.w("MAX_STREAM", "Firebase init warning: ${it.message}")
        }

        // Initialize Extension Manager
        extensionManager = ExtensionManager(this)

        // Start Koin
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}

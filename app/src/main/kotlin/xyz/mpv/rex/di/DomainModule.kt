package xyz.mpv.rex.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import xyz.mpv.rex.App
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.FirebaseAuthManager
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService

val appModule = module {
    single {
        val dbId = androidContext().getString(R.string.firestore_database_id)
        runCatching {
            FirebaseFirestore.getInstance(dbId)
        }.getOrElse { FirebaseFirestore.getInstance() }
    }
    single { FirebaseAuth.getInstance() }
    single { FirebaseAuthManager(androidContext(), get(), get()) }
    single { FirebaseProviderSyncService(androidContext(), get(), get()) }
    single { App.instance.extensionManager }
}

package xyz.mpv.rex.cinehub.provider.server

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import xyz.mpv.rex.R

data class SyncProviderInfo(
    val name: String = "",
    val url: String = "",
    val version: Int = 1,
    val enabled: Boolean = true,
    val authors: String = "",
    val description: String = ""
)

class FirebaseProviderSyncService(
    private val context: Context,
    private val firestore: FirebaseFirestore = runCatching {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }.getOrElse { FirebaseFirestore.getInstance() },
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun fetchAvailableProviders(): List<SyncProviderInfo> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("providers").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(SyncProviderInfo::class.java)
            }
        } catch (e: Exception) {
            Log.e("ProviderSyncService", "Failed to fetch remote providers: ${e.message}")
            emptyList()
        }
    }

    suspend fun publishProvider(info: SyncProviderInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("providers").document(info.name)
                .set(info, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e("ProviderSyncService", "Failed to publish provider: ${e.message}")
            false
        }
    }
}

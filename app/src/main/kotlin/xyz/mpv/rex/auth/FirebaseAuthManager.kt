package xyz.mpv.rex.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.model.UserProfile
import xyz.mpv.rex.auth.model.UserRole

interface AuthManager {
    val currentUserProfile: StateFlow<UserProfile?>
    suspend fun syncUserToFirestore(user: FirebaseUser): Result<UserProfile>
    suspend fun updateUserRole(targetUid: String, newRole: String, newPlan: String): Result<Unit>
    suspend fun getAllUsers(): Result<List<UserProfile>>
    fun signOut()
}

class FirebaseAuthManager(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = runCatching {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }.getOrElse { FirebaseFirestore.getInstance() }
) : AuthManager {

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    override val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                listenToUserProfile(user.uid, user.email)
            } else {
                _currentUserProfile.value = null
            }
        }
    }

    private fun listenToUserProfile(uid: String, email: String?) {
        firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseAuthManager", "Error listening to profile: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    _currentUserProfile.value = UserProfile.fromSnapshot(snapshot)
                } else if (auth.currentUser != null) {
                    val user = auth.currentUser!!
                    val isOwner = (email ?: user.email).equals("sabhiron5@gmail.com", ignoreCase = true)
                    val profile = UserProfile(
                        uid = user.uid,
                        email = user.email ?: "",
                        displayName = user.displayName ?: (if (user.email != null) user.email!!.substringBefore("@") else "User"),
                        photoUrl = user.photoUrl?.toString() ?: "",
                        role = if (isOwner) UserRole.OWNER else UserRole.USER,
                        plan = if (isOwner) "admin" else "free",
                        premium = isOwner,
                        isAdmin = isOwner
                    )
                    _currentUserProfile.value = profile
                    // Persist initial record
                    firestore.collection("users").document(uid).set(profile, SetOptions.merge())
                }
            }
    }

    override suspend fun syncUserToFirestore(user: FirebaseUser): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(user.uid)
            val snapshot = userRef.get().await()

            val isOwner = user.email.equals("sabhiron5@gmail.com", ignoreCase = true)
            val existingRole = snapshot.getString("role") ?: if (isOwner) UserRole.OWNER else UserRole.USER
            val existingPlan = snapshot.getString("plan") ?: if (isOwner) "admin" else "free"
            val photo = user.photoUrl?.toString() ?: snapshot.getString("photoUrl") ?: ""

            val profile = UserProfile(
                uid = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: snapshot.getString("displayName") ?: (user.email?.substringBefore("@") ?: "User"),
                photoUrl = photo,
                role = if (isOwner) UserRole.OWNER else existingRole,
                plan = if (isOwner) "admin" else existingPlan,
                premium = isOwner || snapshot.getBoolean("premium") == true,
                isAdmin = isOwner || existingRole.equals(UserRole.ADMIN, ignoreCase = true),
                createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )

            userRef.set(profile, SetOptions.merge()).await()
            _currentUserProfile.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "syncUserToFirestore failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateUserRole(targetUid: String, newRole: String, newPlan: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                "role" to newRole,
                "plan" to newPlan,
                "isAdmin" to (newRole == UserRole.ADMIN || newRole == UserRole.OWNER),
                "premium" to (newRole != UserRole.USER || newPlan != "free")
            )
            firestore.collection("users").document(targetUid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllUsers(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("users").get().await()
            val list = snapshot.documents.map { doc ->
                UserProfile.fromSnapshot(doc)
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "getAllUsers failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun signOut() {
        auth.signOut()
        _currentUserProfile.value = null
    }
}

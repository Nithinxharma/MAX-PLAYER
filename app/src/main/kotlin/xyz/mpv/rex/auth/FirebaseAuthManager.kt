package xyz.mpv.rex.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import xyz.mpv.rex.auth.model.AuthState
import xyz.mpv.rex.auth.model.UserProfile
import xyz.mpv.rex.auth.model.UserRole

/**
 * Production implementation of [AuthManager] connecting Google Sign-In, Firebase Auth,
 * Cloud Firestore Role-Based Access Control (RBAC), and user profile synchronization.
 */
class FirebaseAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthManager {

    private val tag = "Auth"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Firebase Web Client ID for OAuth Token Exchange
    val defaultWebClientId = "533471513816-kdnn248ctlum2dn6c3jr3m517jhm0l3d.apps.googleusercontent.com"

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    override val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(
        if (auth.currentUser != null) AuthState.Authenticated else AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    override val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _userRole = MutableStateFlow<String>(UserRole.USER)
    override val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _isAdmin = MutableStateFlow<Boolean>(false)
    override val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isPremium = MutableStateFlow<Boolean>(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _firebaseUser.value = user
            if (user != null) {
                Log.d(tag, "[Auth] User Authenticated")
                _authState.value = AuthState.Authenticated
                scope.launch {
                    fetchUserProfile(user.uid)
                }
            } else {
                Log.d(tag, "[Auth] User Unauthenticated")
                _authState.value = AuthState.Unauthenticated
                _userProfile.value = null
                _userRole.value = UserRole.USER
                _isAdmin.value = false
                _isPremium.value = false
            }
        }
    }

    override fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(defaultWebClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    override suspend fun signInWithGoogleToken(idToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            Log.d(tag, "[Firebase Auth] Initiating Google Credential Authentication")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: return@withContext Result.failure<UserProfile>(
                IllegalStateException("FirebaseUser is null after successful Google authentication")
            ).also {
                _authState.value = AuthState.Error("Authentication returned empty user")
            }

            Log.d(tag, "[Firebase Auth] Authentication Success. UID: ${user.uid}")
            val profileResult = syncUserToFirestore(user)
            if (profileResult.isSuccess) {
                _authState.value = AuthState.Authenticated
            } else {
                _authState.value = AuthState.Authenticated // Auth succeeded even if offline sync pending
            }
            profileResult
        } catch (e: Exception) {
            Log.e(tag, "[Firebase Auth] Sign-in failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    override suspend fun handleGoogleSignInResult(data: Intent?): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken ?: return@withContext Result.failure(
                IllegalStateException("Google ID Token is missing from sign-in response")
            )
            signInWithGoogleToken(idToken)
        } catch (e: Exception) {
            Log.e(tag, "[Google Sign-In] Failed to process sign-in result", e)
            _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            Result.failure(e)
        }
    }

    override suspend fun syncUserToFirestore(user: FirebaseUser): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(user.uid)
            val snapshot = userRef.get().await()

            val existingProfile = snapshot.toObject(UserProfile::class.java)
            val isExisting = snapshot.exists()

            val resolvedRole = existingProfile?.role ?: UserRole.USER
            val resolvedPremium = existingProfile?.premium ?: false

            val updatePayload = hashMapOf<String, Any?>(
                "uid" to user.uid,
                "name" to (user.displayName ?: existingProfile?.name ?: "MaxStream User"),
                "email" to (user.email ?: existingProfile?.email ?: ""),
                "photo" to (user.photoUrl?.toString() ?: existingProfile?.photo ?: ""),
                "role" to resolvedRole,
                "premium" to resolvedPremium,
                "lastLogin" to FieldValue.serverTimestamp()
            )

            if (!isExisting) {
                updatePayload["createdAt"] = FieldValue.serverTimestamp()
            }

            userRef.set(updatePayload, SetOptions.merge()).await()
            Log.d(tag, "[Firestore] Synchronized user record at users/${user.uid}")

            // Re-fetch clean document
            val freshDoc = userRef.get().await()
            val finalProfile = freshDoc.toObject(UserProfile::class.java) ?: UserProfile(
                uid = user.uid,
                name = user.displayName,
                email = user.email,
                photo = user.photoUrl?.toString(),
                role = resolvedRole,
                premium = resolvedPremium
            )

            applyProfileState(finalProfile)
            Result.success(finalProfile)
        } catch (e: Exception) {
            Log.e(tag, "[Firestore] Failed to sync user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun fetchUserProfile(uid: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc != null && doc.exists()) {
                val profile = doc.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
                applyProfileState(profile)
                Log.d(tag, "[Firestore] Fetched user profile for $uid: $profile")
                Result.success(profile)
            } else {
                Log.d(tag, "[Firestore] User profile document missing for $uid")
                // Create baseline profile for authenticated user
                val baseProfile = UserProfile(uid = uid)
                applyProfileState(baseProfile)
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(tag, "[Firestore] Error fetching profile for $uid", e)
            Result.failure(e)
        }
    }

    private fun applyProfileState(profile: UserProfile) {
        _userProfile.value = profile
        
        val role = profile.role.ifBlank { UserRole.USER }
        _userRole.value = role
        _isAdmin.value = UserRole.isAdmin(role)
        _isPremium.value = profile.premium

        Log.d(tag, "[Auth] Role Loaded: $role")
        Log.d(tag, "[Auth] Premium Loaded: ${profile.premium}")
    }

    override suspend fun signOut(context: Context?): Unit = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            context?.let {
                try {
                    getGoogleSignInClient(it).signOut().await()
                } catch (e: Exception) {
                    Log.w(tag, "Google client sign-out warning: ${e.message}")
                }
            }
            _firebaseUser.value = null
            _authState.value = AuthState.Unauthenticated
            _userProfile.value = null
            _userRole.value = UserRole.USER
            _isAdmin.value = false
            _isPremium.value = false
            Log.d(tag, "[Auth] User Unauthenticated (Signed out)")
        } catch (e: Exception) {
            Log.e(tag, "[Firebase Auth] Error during sign-out", e)
        }
        Unit
    }
}

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
import com.google.firebase.firestore.ListenerRegistration
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
 * Realtime Cloud Firestore Role-Based Access Control (RBAC), and user profile synchronization.
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

    // Realtime Firestore snapshot listener for the active user's document
    private var profileSnapshotListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _firebaseUser.value = user
            if (user != null) {
                Log.d(tag, "[Auth] User Authenticated (UID: ${user.uid})")
                _authState.value = AuthState.Authenticated
                
                // Immediately start listening for Realtime Firestore changes on users/{uid}
                attachRealtimeProfileListener(user.uid)
                
                // Perform sync in background
                scope.launch {
                    try {
                        syncUserToFirestore(user)
                    } catch (e: Exception) {
                        Log.w(tag, "[Firestore] Background initial sync: ${e.message}")
                    }
                }
            } else {
                Log.d(tag, "[Auth] User Unauthenticated")
                detachRealtimeProfileListener()
                _authState.value = AuthState.Unauthenticated
                _userProfile.value = null
                _userRole.value = UserRole.USER
                _isAdmin.value = false
                _isPremium.value = false
            }
        }
    }

    /**
     * Attaches a real-time Firestore DocumentSnapshot listener so any update in the Firebase Console
     * (e.g. changing name, photo, photoUrl, or role to 'admin') immediately reflects in the UI
     * without requiring an app restart or re-login.
     */
    private fun attachRealtimeProfileListener(uid: String) {
        detachRealtimeProfileListener()
        try {
            Log.d(tag, "[Firestore] Attaching Realtime Profile Listener for users/$uid")
            profileSnapshotListener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(tag, "[Firestore] Realtime profile listener error: ${error.message}", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val parsedProfile = UserProfile.fromSnapshot(snapshot)
                        Log.d(tag, "[Firestore] Realtime Update received for $uid: name=${parsedProfile.name}, role=${parsedProfile.role}, isAdmin=${UserRole.isAdmin(parsedProfile.role)}")
                        applyProfileState(parsedProfile)
                    } else {
                        Log.w(tag, "[Firestore] Realtime snapshot: document does not exist for $uid")
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "[Firestore] Failed to attach snapshot listener: ${e.message}", e)
        }
    }

    private fun detachRealtimeProfileListener() {
        profileSnapshotListener?.remove()
        profileSnapshotListener = null
    }

    override fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val webClientId = try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else defaultWebClientId
        } catch (e: Exception) {
            defaultWebClientId
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    override suspend fun signInWithGoogleToken(idToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            Log.d(tag, "[Firebase Auth] Initiating Google Credential Authentication with token length: ${idToken.length}")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: return@withContext Result.failure<UserProfile>(
                IllegalStateException("FirebaseUser is null after successful Google authentication")
            ).also {
                _authState.value = AuthState.Error("Authentication returned empty user")
            }

            Log.d(tag, "[Firebase Auth] Authentication Success! UID: ${user.uid}, Email: ${user.email}")
            _firebaseUser.value = user
            _authState.value = AuthState.Authenticated

            // Attach realtime listener immediately
            attachRealtimeProfileListener(user.uid)

            // Sync user to Firestore without overwriting remote customizations
            val syncResult = syncUserToFirestore(user)
            val profile = syncResult.getOrNull() ?: UserProfile(
                uid = user.uid,
                name = user.displayName ?: "MaxStream User",
                email = user.email ?: "",
                photo = user.photoUrl?.toString(),
                role = UserRole.USER,
                premium = false
            )
            applyProfileState(profile)

            Result.success(profile)
        } catch (e: Exception) {
            Log.e(tag, "[Firebase Auth] Sign-in failed: ${e.message}", e)
            val errorMsg = if (e is ApiException) {
                when (e.statusCode) {
                    10 -> {
                        "Google Sign-In Developer Error (Status 10). " +
                        "The SHA-1 fingerprint of this APK must be registered in Firebase Console (Project Settings -> Your Apps). " +
                        "Navigate to Settings -> About in this app to copy the active SHA-1 fingerprint."
                    }
                    12500 -> {
                        "Google Sign-In Error (Status 12500). Please verify Google Play Services and ensure the OAuth Client ID is registered in Firebase."
                    }
                    7 -> "Network error during Google Sign-In. Check your internet connection."
                    else -> "Google Sign-In failed [code: ${e.statusCode}]: ${e.localizedMessage ?: e.message}"
                }
            } else {
                e.localizedMessage ?: "Authentication failed: ${e.message}"
            }
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(Exception(errorMsg, e))
        }
    }

    override suspend fun handleGoogleSignInResult(data: Intent?): Result<UserProfile> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken ?: throw IllegalStateException("Google ID Token is null from account")
            signInWithGoogleToken(idToken)
        } catch (e: ApiException) {
            Log.e(tag, "[Firebase Auth] GoogleSignIn error code: ${e.statusCode}", e)
            val errorMsg = when (e.statusCode) {
                10 -> {
                    "Google Sign-In Developer Error (Status 10). " +
                    "The SHA-1 fingerprint of this build must be registered in Firebase Console (Project Settings -> Android Apps). " +
                    "View and copy your active SHA-1 in Settings -> About."
                }
                12500 -> {
                    "Google Sign-In Error (Status 12500). Verify SHA-1 and OAuth client settings in Firebase."
                }
                7 -> "Network error during Google Sign-In. Check your internet connection."
                else -> "Google Sign-In failed [code: ${e.statusCode}]: ${e.localizedMessage ?: e.message}"
            }
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(Exception(errorMsg, e))
        } catch (e: Exception) {
            Log.e(tag, "[Firebase Auth] Google Sign-In intent parsing failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-in parsing error")
            Result.failure(e)
        }
    }

    /**
     * Safely updates or creates the user document in Firestore.
     * CRITICAL: Preserves existing Firestore fields (such as 'role', 'name', 'photo', 'premium')
     * so that manual modifications made in the Firebase Console are NOT overridden by sign-in!
     */
    override suspend fun syncUserToFirestore(user: FirebaseUser): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(user.uid)
            val snapshot = userRef.get().await()

            val existingProfile = if (snapshot.exists()) UserProfile.fromSnapshot(snapshot) else null
            val isExisting = snapshot.exists()

            // Respect existing roles and customized names/photos from Firestore
            val resolvedRole = existingProfile?.role?.takeIf { it.isNotBlank() } ?: UserRole.USER
            val resolvedPremium = existingProfile?.premium ?: false
            val resolvedName = existingProfile?.name?.takeIf { it.isNotBlank() } 
                ?: user.displayName 
                ?: "MaxStream User"
            val resolvedEmail = existingProfile?.email?.takeIf { it.isNotBlank() } 
                ?: user.email 
                ?: ""
            val resolvedPhoto = existingProfile?.photo?.takeIf { it.isNotBlank() } 
                ?: user.photoUrl?.toString()

            val updatePayload = hashMapOf<String, Any?>(
                "uid" to user.uid,
                "name" to resolvedName,
                "email" to resolvedEmail,
                "photo" to resolvedPhoto,
                "role" to resolvedRole,
                "premium" to resolvedPremium,
                "lastLogin" to FieldValue.serverTimestamp()
            )

            if (!isExisting) {
                updatePayload["createdAt"] = FieldValue.serverTimestamp()
            }

            userRef.set(updatePayload, SetOptions.merge()).await()
            Log.d(tag, "[Firestore] Synchronized user record at users/${user.uid} (Role: $resolvedRole)")

            // Fetch clean document with parser
            val freshDoc = userRef.get().await()
            val finalProfile = if (freshDoc.exists()) {
                UserProfile.fromSnapshot(freshDoc)
            } else {
                UserProfile(
                    uid = user.uid,
                    name = resolvedName,
                    email = resolvedEmail,
                    photo = resolvedPhoto,
                    role = resolvedRole,
                    premium = resolvedPremium
                )
            }

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
                val profile = UserProfile.fromSnapshot(doc)
                applyProfileState(profile)
                Log.d(tag, "[Firestore] Fetched user profile for $uid: $profile")
                Result.success(profile)
            } else {
                Log.d(tag, "[Firestore] User profile document missing for $uid")
                // Create baseline profile for authenticated user
                val authUser = auth.currentUser
                val baseProfile = UserProfile(
                    uid = uid,
                    name = authUser?.displayName ?: "MaxStream User",
                    email = authUser?.email ?: "",
                    photo = authUser?.photoUrl?.toString(),
                    role = UserRole.USER
                )
                applyProfileState(baseProfile)
                Result.success(baseProfile)
            }
        } catch (e: Exception) {
            Log.e(tag, "[Firestore] Error fetching profile for $uid", e)
            Result.failure(e)
        }
    }

    private fun applyProfileState(profile: UserProfile) {
        // Fallback to static "MaxStream User" if name is missing or blank
        val cleanName = profile.name?.takeIf { it.isNotBlank() } 
            ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() } 
            ?: "MaxStream User"

        val cleanEmail = profile.email?.takeIf { it.isNotBlank() } 
            ?: auth.currentUser?.email 
            ?: ""

        val cleanPhoto = profile.photo?.takeIf { it.isNotBlank() } 
            ?: auth.currentUser?.photoUrl?.toString()

        val role = profile.role.trim().ifBlank { UserRole.USER }
        val admin = UserRole.isAdmin(role)

        val cleanProfile = profile.copy(
            name = cleanName,
            email = cleanEmail,
            photo = cleanPhoto,
            role = role
        )

        _userProfile.value = cleanProfile
        _userRole.value = role
        _isAdmin.value = admin
        _isPremium.value = cleanProfile.premium

        Log.d(tag, "[Auth] Profile Applied -> Name: $cleanName | Role: $role | IsAdmin: $admin | Premium: ${cleanProfile.premium}")
    }

    override suspend fun signOut(context: Context?): Unit = withContext(Dispatchers.IO) {
        try {
            detachRealtimeProfileListener()
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

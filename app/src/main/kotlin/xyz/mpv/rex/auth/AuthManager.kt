package xyz.mpv.rex.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.StateFlow
import xyz.mpv.rex.auth.model.AuthState
import xyz.mpv.rex.auth.model.UserProfile

/**
 * Clean architectural abstraction for Max Stream Authentication, Role-Based Access Control (RBAC),
 * and Firestore Profile Synchronization.
 */
interface AuthManager {
    /**
     * Currently active Firebase user, or null if unauthenticated.
     */
    val currentUser: FirebaseUser?

    /**
     * Observable reactive stream of raw Firebase User entity.
     */
    val firebaseUser: StateFlow<FirebaseUser?>

    /**
     * Observable reactive stream of high-level authentication lifecycle state.
     */
    val authState: StateFlow<AuthState>

    /**
     * Observable reactive stream of the Firestore user profile (`users/{uid}`).
     */
    val userProfile: StateFlow<UserProfile?>

    /**
     * Observable reactive stream of user role ("user", "admin", "moderator", etc.).
     */
    val userRole: StateFlow<String>

    /**
     * Reactive flag indicating whether the authenticated user holds administrative privileges.
     */
    val isAdmin: StateFlow<Boolean>

    /**
     * Reactive flag indicating whether the authenticated user holds an active Premium tier.
     */
    val isPremium: StateFlow<Boolean>

    /**
     * Checks if a user is currently authenticated with Firebase.
     */
    val isAuthenticated: Boolean
        get() = currentUser != null

    /**
     * Instantiates and configures a GoogleSignInClient with the Firebase Web Client ID.
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient

    /**
     * Authenticates with Firebase using a Google OAuth ID token, retrieves UID, and updates Firestore.
     */
    suspend fun signInWithGoogleToken(idToken: String): Result<UserProfile>

    /**
     * Signs in using an Intent received from GoogleSignIn result.
     */
    suspend fun handleGoogleSignInResult(data: Intent?): Result<UserProfile>

    /**
     * Retrieves user profile from Firestore `users/{uid}`.
     */
    suspend fun fetchUserProfile(uid: String): Result<UserProfile?>

    /**
     * Creates or updates the user document in Firestore `users/{uid}` with server timestamps.
     */
    suspend fun syncUserToFirestore(user: FirebaseUser): Result<UserProfile>

    /**
     * Signs out the current user from Firebase and Google Sign-In client.
     */
    suspend fun signOut(context: Context? = null)
}

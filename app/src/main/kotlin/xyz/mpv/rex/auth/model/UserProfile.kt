package xyz.mpv.rex.auth.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data representation of a Max Stream authenticated user in Firestore (`users/{uid}`).
 * Designed with safe fallback defaults for backward compatibility.
 */
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val name: String? = null,
    val email: String? = null,
    val photo: String? = null,
    val role: String = UserRole.USER,
    val premium: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val lastLogin: Date? = null
)

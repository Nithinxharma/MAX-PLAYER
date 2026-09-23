package xyz.mpv.rex.auth.model

/**
 * Robust reactive Auth State representation for Max Stream startup, session lifecycle,
 * and access control routing.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object Authenticated : AuthState
    data object Unauthenticated : AuthState
    data class Error(val message: String) : AuthState
}

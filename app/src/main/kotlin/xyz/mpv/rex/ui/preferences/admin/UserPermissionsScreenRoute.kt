package xyz.mpv.rex.ui.preferences.admin

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object UserPermissionsScreenRoute : Screen {
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        UserPermissionsScreen(
            onNavigateBack = { backstack.removeLastOrNull() }
        )
    }
}

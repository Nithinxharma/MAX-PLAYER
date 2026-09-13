package xyz.mpv.rex.ui.preferences

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object InstalledExtensionsScreenRoute : Screen {
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        InstalledExtensionsScreen(
            onNavigateBack = { backstack.removeLastOrNull() },
            onNavigateToRepositories = {
                backstack.add(ExtensionRepositoriesScreenRoute)
            }
        )
    }
}

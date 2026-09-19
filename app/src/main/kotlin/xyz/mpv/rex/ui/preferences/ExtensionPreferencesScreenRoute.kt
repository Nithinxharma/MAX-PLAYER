package xyz.mpv.rex.ui.preferences

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object ExtensionPreferencesScreenRoute : Screen {
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        ExtensionPreferencesScreen(
            onNavigateBack = { backstack.removeLastOrNull() },
            onNavigateToRepositories = { backstack.add(ExtensionRepositoriesScreenRoute) },
            onNavigateToInstalled = { backstack.add(InstalledExtensionsScreenRoute) },
            onNavigateToTestCenter = { backstack.add(xyz.mpv.rex.cinehub.diagnostic.CloudStreamTestCenterScreen) },
            onNavigateToForceActivation = { backstack.add(ForcePluginActivationScreenRoute) }
        )
    }
}

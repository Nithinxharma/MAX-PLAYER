package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
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
            onNavigateToPresets = { backstack.add(RepositoryPresetsScreenRoute) },
            onNavigateToInstalled = { backstack.add(InstalledExtensionsScreenRoute) },
            onNavigateToPlans = { backstack.add(xyz.mpv.rex.ui.preferences.admin.PlansManagementScreenRoute) },
            onNavigateToUserPermissions = { backstack.add(xyz.mpv.rex.ui.preferences.admin.UserPermissionsScreenRoute) },
            onNavigateToAssignment = { backstack.add(xyz.mpv.rex.ui.preferences.admin.ExtensionAssignmentScreenRoute) }
        )
    }
}

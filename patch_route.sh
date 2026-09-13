cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionPreferencesScreenRoute.kt
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
            onNavigateBack = { backstack.removeLastOrNull() }
        )
    }
}
INNER_EOF

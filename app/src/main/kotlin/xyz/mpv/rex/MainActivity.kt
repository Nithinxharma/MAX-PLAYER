package xyz.mpv.rex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.koin.android.ext.android.inject
import xyz.mpv.rex.auth.FirebaseAuthManager
import xyz.mpv.rex.cinehub.diagnostic.CloudStreamQueryTerminalView
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.ui.extensions.ExtensionsScreen
import xyz.mpv.rex.ui.home.HomeScreen
import xyz.mpv.rex.ui.player.PlayerScreen
import xyz.mpv.rex.ui.profile.ProfileScreen
import xyz.mpv.rex.ui.search.SearchScreen
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

enum class ScreenTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    EXTENSIONS("Extensions", Icons.Default.Extension),
    TERMINAL("Test Center", Icons.Default.Terminal),
    PROFILE("Profile", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    private val extensionManager: ExtensionManager by inject()
    private val authManager: FirebaseAuthManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaxStreamTheme {
                var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
                var playingMedia by remember { mutableStateOf<Pair<String, String>?>(null) }

                BackHandler(enabled = playingMedia != null || currentTab != ScreenTab.HOME) {
                    if (playingMedia != null) {
                        playingMedia = null
                    } else if (currentTab != ScreenTab.HOME) {
                        currentTab = ScreenTab.HOME
                    }
                }

                if (playingMedia != null) {
                    PlayerScreen(
                        videoTitle = playingMedia!!.first,
                        videoUrl = playingMedia!!.second,
                        onBack = { playingMedia = null }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaxStreamTheme.MidnightSurface,
                                contentColor = MaxStreamTheme.TextPrimary
                            ) {
                                ScreenTab.values().forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab,
                                        onClick = { currentTab = tab },
                                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                                        label = { Text(tab.title) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaxStreamTheme.CrimsonAccent,
                                            selectedTextColor = MaxStreamTheme.CrimsonAccent,
                                            indicatorColor = MaxStreamTheme.ElevatedSurface,
                                            unselectedIconColor = MaxStreamTheme.TextSecondary,
                                            unselectedTextColor = MaxStreamTheme.TextSecondary
                                        )
                                    )
                                }
                            }
                        },
                        containerColor = MaxStreamTheme.AbyssBackground,
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                ScreenTab.HOME -> HomeScreen(
                                    onPlayMedia = { title, url -> playingMedia = title to url },
                                    onNavigateToSearch = { currentTab = ScreenTab.SEARCH },
                                    onOpenTestCenter = { currentTab = ScreenTab.TERMINAL }
                                )
                                ScreenTab.SEARCH -> SearchScreen(
                                    onPlayMedia = { title, url -> playingMedia = title to url }
                                )
                                ScreenTab.EXTENSIONS -> ExtensionsScreen(
                                    extensionManager = extensionManager,
                                    onOpenTestCenter = { currentTab = ScreenTab.TERMINAL }
                                )
                                ScreenTab.TERMINAL -> CloudStreamQueryTerminalView(
                                    onBack = { currentTab = ScreenTab.HOME }
                                )
                                ScreenTab.PROFILE -> ProfileScreen(
                                    authManager = authManager
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

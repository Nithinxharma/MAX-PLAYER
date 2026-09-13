package xyz.mpv.rex.ui.browser.shorts
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Serializable
object RexShortsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val tabs = listOf("Online", "Local", "History", "Downloads")
        
        Scaffold(
            topBar = {
                BrowserTopBar(
                    title = "RexShorts",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = 0,
                    onCancelSelection = {},
                    isHomeScreen = true,
                    onSearchClick = {}
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> OnlineShortsGridScreen.Content()
                        1 -> Text("Local Shorts (To be implemented)")
                        2 -> Text("History (To be implemented)")
                        3 -> Text("Downloads (To be implemented)")
                    }
                }
            }
        }
    }
}

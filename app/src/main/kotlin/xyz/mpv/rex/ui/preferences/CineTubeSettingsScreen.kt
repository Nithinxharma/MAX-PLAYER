package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import xyz.mpv.rex.preferences.preference.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.preferences.GroupedPreferenceCard
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.Preference
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object CineTubeSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val browserPreferences: BrowserPreferences = koinInject()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CineTube Settings") },
                    navigationIcon = {
                        IconButton(onClick = { backstack.removeLastOrNull() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    GroupedListColumn {
                        GroupedPreferenceCard(
                            position = GroupPosition.FIRST,
                            highlightKey = R.string.pref_appearance_tab_cinetube_title,
                        ) {
                            SwitchPreference(
                                value = browserPreferences.enableTabCineTube.collectAsState().value,
                                onValueChange = { browserPreferences.enableTabCineTube.set(it) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_tab_cinetube_title)) },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_tab_cinetube_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                            )
                        }
                        
                        GroupedPreferenceCard(
                            position = GroupPosition.MIDDLE,
                            highlightKey = 0,
                        ) {
                            Preference(
                                title = { Text(text = "API Status") },
                                summary = { Text(text = "Connected via Invidious nodes", color = MaterialTheme.colorScheme.outline) },
                                onClick = {}
                            )
                        }

                        GroupedPreferenceCard(
                            position = GroupPosition.MIDDLE,
                            highlightKey = 0,
                        ) {
                            Preference(
                                title = { Text(text = "Region") },
                                summary = { Text(text = "IN", color = MaterialTheme.colorScheme.outline) },
                                onClick = {}
                            )
                        }

                        GroupedPreferenceCard(
                            position = GroupPosition.MIDDLE,
                            highlightKey = 0,
                        ) {
                            Preference(
                                title = { Text(text = "Search Preferences") },
                                summary = { Text(text = "Movies & Music", color = MaterialTheme.colorScheme.outline) },
                                onClick = {}
                            )
                        }

                        GroupedPreferenceCard(
                            position = GroupPosition.MIDDLE,
                            highlightKey = 0,
                        ) {
                            Preference(
                                title = { Text(text = "Cache") },
                                summary = { Text(text = "Clear CineTube cached data", color = MaterialTheme.colorScheme.outline) },
                                onClick = {}
                            )
                        }

                        GroupedPreferenceCard(
                            position = GroupPosition.LAST,
                            highlightKey = 0,
                        ) {
                            Preference(
                                title = { Text(text = "API Client") },
                                summary = { Text(text = "CineTubeApiClient", color = MaterialTheme.colorScheme.outline) },
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

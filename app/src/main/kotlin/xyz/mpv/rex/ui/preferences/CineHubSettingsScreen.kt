package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.preferences.GroupedPreferenceCard
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.cinehub.data.KodiMediaScraper
import xyz.mpv.rex.cinehub.data.CineFolderMetadataManager
import java.io.File

import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object CineHubSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val context = LocalContext.current
        val browserPreferences: BrowserPreferences = koinInject()
        val scope = rememberCoroutineScope()

        var showApiKeyDialog by remember { mutableStateOf(false) }
        var tempApiKey by remember { mutableStateOf("") }
        var isScanning by remember { mutableStateOf(false) }
        var scanProgress by remember { mutableStateOf(0f) }
        var scanStatus by remember { mutableStateOf("") }

        val apiKey = browserPreferences.customTmdbApiKey.collectAsState().value

        if (showApiKeyDialog) {
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                title = { Text("TMDb API Key") },
                text = {
                    Column {
                        Text("Enter your custom TMDb API Key for the Media Scraper.", modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = tempApiKey,
                            onValueChange = { tempApiKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        browserPreferences.customTmdbApiKey.set(tempApiKey)
                        showApiKeyDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (isScanning) {
            AlertDialog(
                onDismissRequest = { /* Cannot dismiss while scanning */ },
                title = { Text("Scanning Library") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(progress = { scanProgress }, modifier = Modifier.padding(16.dp))
                        Text(scanStatus)
                    }
                },
                confirmButton = { }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CineHub & Media Scraper Settings") },
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
            ProvidePreferenceLocals {
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
                                position = GroupPosition.FIRST
                            ) {
                                SwitchPreference(
                                    value = browserPreferences.enableTabCineHub.collectAsState().value,
                                    onValueChange = { browserPreferences.enableTabCineHub.set(it) },
                                    title = { Text(text = "Enable CineHub Tab") },
                                    summary = {
                                        Text(
                                            text = "Show the CineHub tab on the main screen.",
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                )
                            }
                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE
                            ) {
                                SwitchPreference(
                                    value = browserPreferences.enableMovieScraper.collectAsState().value,
                                    onValueChange = { browserPreferences.enableMovieScraper.set(it) },
                                    title = { Text(text = "Enable Movie Scraper") },
                                    summary = {
                                        Text(
                                            text = "Automatically scrape movie metadata and artwork.",
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                )
                            }
                            GroupedPreferenceCard(
                                position = GroupPosition.MIDDLE
                            ) {
                                SwitchPreference(
                                    value = browserPreferences.enableTvScraper.collectAsState().value,
                                    onValueChange = { browserPreferences.enableTvScraper.set(it) },
                                    title = { Text(text = "Enable TV Show Scraper") },
                                    summary = {
                                        Text(
                                            text = "Automatically scrape TV series metadata and episodes.",
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                )
                            }
                            GroupedPreferenceCard(
                                position = GroupPosition.LAST
                            ) {
                                Preference(
                                    title = { Text("TMDb API Key") },
                                    summary = {
                                        Text(
                                            text = if (apiKey.isNotBlank()) "Key is configured" else "No key configured (Requires API Key)",
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    },
                                    onClick = {
                                        tempApiKey = apiKey
                                        showApiKeyDialog = true
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Text("Library Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        
                        GroupedListColumn {
                            GroupedPreferenceCard(
                                position = GroupPosition.FIRST
                            ) {
                                Preference(
                                    title = { Text("Scan Library") },
                                    summary = {
                                        Text(
                                            text = "Find and scrape missing metadata",
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    },
                                    onClick = {
                                        if (apiKey.isBlank()) {
                                            Toast.makeText(context, "Please configure TMDb API Key first", Toast.LENGTH_SHORT).show()
                                            return@Preference
                                        }
                                        // Trigger Scan
                                        scope.launch(Dispatchers.IO) {
                                            withContext(Dispatchers.Main) {
                                                isScanning = true
                                                scanProgress = 0f
                                                scanStatus = "Initializing..."
                                            }
                                            
                                            val folders = xyz.mpv.rex.repository.MediaFileRepository.getAllVideoFolders(context.applicationContext as android.app.Application)
                                            val total = folders.size
                                            var current = 0
                                            
                                            for (folder in folders) {
                                                current++
                                                withContext(Dispatchers.Main) {
                                                    scanStatus = "Scanning ${folder.name}"
                                                    scanProgress = current.toFloat() / total
                                                }
                                                try {
                                                    KodiMediaScraper.scrapeDirectory(
                                                        context = context,
                                                        directory = File(folder.path),
                                                        downloadArtworkAndNfo = true,
                                                        onProgress = { c, t, name ->
                                                            // Optional granular progress
                                                        }
                                                    )
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            
                                            withContext(Dispatchers.Main) {
                                                isScanning = false
                                                Toast.makeText(context, "Library scan complete!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }

                            GroupedPreferenceCard(
                                position = GroupPosition.LAST
                            ) {
                                Preference(
                                    title = { Text("Clear Metadata Cache", color = MaterialTheme.colorScheme.error) },
                                    summary = {
                                        Text(
                                            text = "Remove all cached items and start fresh",
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    },
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            xyz.mpv.rex.cinehub.data.MetadataCacheManager.clearCache(context)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

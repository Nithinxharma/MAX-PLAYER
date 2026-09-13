package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.manager.RepositoryManager
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionRepositoriesScreen(
    onNavigateBack: () -> Unit,
    repositoryManager: RepositoryManager = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repos by repositoryManager.getAllRepositories().collectAsState(initial = emptyList())
    var isSyncingAll by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<ExtensionRepo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extension Repositories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSyncingAll = true
                            scope.launch(Dispatchers.IO) {
                                val results = repositoryManager.syncAllRepositories()
                                withContext(Dispatchers.Main) {
                                    isSyncingAll = false
                                    Toast.makeText(context, "Synced ${results.size} repositories", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSyncingAll
                    ) {
                        if (isSyncingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add Repository") }
            )
        }
    ) { paddingValues ->
        if (repos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No Repositories Added",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add CloudStream or community repositories to discover and install provider extensions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Repository Now")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repos, key = { it.url }) { repo ->
                    RepositoryCard(
                        repo = repo,
                        onSync = {
                            scope.launch(Dispatchers.IO) {
                                val result = repositoryManager.syncRepository(repo.url)
                                withContext(Dispatchers.Main) {
                                    if (result.error == null) {
                                        Toast.makeText(context, "Synced ${result.plugins.size} plugins from ${repo.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sync error: ${result.error}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onDelete = { repoToDelete = repo }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, name ->
                showAddDialog = false
                scope.launch(Dispatchers.IO) {
                    repositoryManager.addRepository(url, name)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Repository added & syncing…", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    repoToDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Remove Repository") },
            text = { Text("Are you sure you want to remove \"${repo.name}\"? Installed extensions from this repository will remain installed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = repoToDelete
                        repoToDelete = null
                        if (target != null) {
                            scope.launch(Dispatchers.IO) {
                                repositoryManager.removeRepository(target)
                            }
                        }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RepositoryCard(
    repo: ExtensionRepo,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(repo.lastSync) {
        if (repo.lastSync > 0) {
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(repo.lastSync))
        } else "Never"
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = repo.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last synced: $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Outlined.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String?) -> Unit
) {
    var repoUrl by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Extension Repository") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter a repository URL (repo.json or plugins.json) or pick a popular community repository preset below:",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://.../repo.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text("Repository Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Popular Community Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                RepositoryManager.POPULAR_PRESETS.forEach { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repoUrl = preset.url
                                repoName = preset.name
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(preset.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(preset.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoUrl.isNotBlank()) {
                        onAdd(repoUrl, repoName.ifBlank { null })
                    }
                },
                enabled = repoUrl.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

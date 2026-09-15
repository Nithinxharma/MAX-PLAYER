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
                            scope.launch(Dispatchers.IO) {
                                isSyncingAll = true
                                repositoryManager.syncAllRepositories()
                                withContext(Dispatchers.Main) {
                                    isSyncingAll = false
                                    Toast.makeText(context, "Repositories Synced", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSyncingAll
                    ) {
                        if (isSyncingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Repository")
            }
        }
    ) { padding ->
        if (repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Source, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Repositories", style = MaterialTheme.typography.titleLarge)
                    Text("Add a repository to install extensions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 16.dp, bottom = 80.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repos, key = { it.url }) { repo ->
                    RepositoryCard(
                        repo = repo,
                        onSync = {
                            scope.launch(Dispatchers.IO) {
                                repositoryManager.syncRepository(repo.url)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Synced ${repo.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = { repoToDelete = repo }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddRepositoryDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { url, name ->
                    scope.launch(Dispatchers.IO) {
                        repositoryManager.addRepository(url, name ?: "Custom Repo")
                        withContext(Dispatchers.Main) {
                            showAddDialog = false
                            Toast.makeText(context, "Repository Added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        repoToDelete?.let { repo ->
            AlertDialog(
                onDismissRequest = { repoToDelete = null },
                title = { Text("Remove Repository?") },
                text = { Text("Are you sure you want to remove '${repo.name}'? Installed extensions will remain but won't receive updates.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                repositoryManager.removeRepository(repo.url)
                                withContext(Dispatchers.Main) {
                                    repoToDelete = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
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
}

@Composable
fun RepositoryCard(
    repo: ExtensionRepo,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    var isSyncing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = repo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(
                onClick = {
                    isSyncing = true
                    onSync()
                    isSyncing = false // Usually handled by callback, simplified for demo
                },
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Sync, contentDescription = "Sync")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String?) -> Unit
) {
    var repoUrl by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

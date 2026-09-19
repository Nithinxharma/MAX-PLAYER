package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.extension.model.ExtensionRepo
import xyz.mpv.rex.cinehub.extension.model.RepoPresetItem
import xyz.mpv.rex.cinehub.extension.model.RepoVerificationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryPresetsScreen(
    onNavigateBack: () -> Unit,
    viewModel: RepositoryPresetsViewModel = koinInject()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val installedRepos by viewModel.installedRepositories.collectAsState()
    val verificationMap by viewModel.verificationMap.collectAsState()
    val isVerifying by viewModel.isVerifying.collectAsState()
    val isSyncingAll by viewModel.isSyncingAll.collectAsState()
    val isInstallingMegaRepo by viewModel.isInstallingMegaRepo.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val megaRepoResult by viewModel.megaRepoResult.collectAsState()
    val syncResults by viewModel.syncResults.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Presets Catalog, 1: Installed Repos, 2: Sync & Diagnostics
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var importExportJsonText by remember { mutableStateOf("") }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Repository Presets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${viewModel.builtInPresets.size} Presets • ${installedRepos.size} Installed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.verifyAllPresets() },
                        enabled = !isVerifying
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Speed, contentDescription = "Verify All Presets")
                        }
                    }
                    IconButton(
                        onClick = { viewModel.syncAllRepositories() },
                        enabled = !isSyncingAll
                    ) {
                        if (isSyncingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync All")
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                importExportJsonText = viewModel.exportJson()
                                showImportExportDialog = true
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = "Import / Export JSON")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // MEGAREPO HERO CARD
            item {
                MegaRepoBannerCard(
                    isInstalling = isInstallingMegaRepo,
                    onInstallClick = { viewModel.installMegaRepo() },
                    megaRepoResult = megaRepoResult
                )
            }

            // ACTION BAR
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.enableAllPresets() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable All Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showAddCustomDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Custom", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // TABS
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Presets (${viewModel.builtInPresets.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Installed (${installedRepos.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Sync Status", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            // SEARCH FILTER
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search repositories or providers...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // TAB 0: PRESET CATALOG
            if (selectedTab == 0) {
                val filteredPresets = viewModel.builtInPresets.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true) ||
                            it.url.contains(searchQuery, ignoreCase = true) ||
                            it.author.contains(searchQuery, ignoreCase = true)
                }

                if (filteredPresets.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No presets matched your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filteredPresets, key = { it.url }) { preset ->
                        val isInstalled = installedRepos.any { it.url.equals(preset.url, ignoreCase = true) }
                        val verification = verificationMap[preset.url]

                        PresetCardItem(
                            preset = preset,
                            isInstalled = isInstalled,
                            verification = verification,
                            onEnable = { viewModel.enablePreset(preset) },
                            onVerify = { viewModel.verifyRepository(preset.url) },
                            onCopyUrl = {
                                clipboardManager.setText(AnnotatedString(preset.url))
                                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // TAB 1: INSTALLED REPOSITORIES
            if (selectedTab == 1) {
                val filteredInstalled = installedRepos.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.url.contains(searchQuery, ignoreCase = true) ||
                            (it.description?.contains(searchQuery, ignoreCase = true) == true)
                }

                if (filteredInstalled.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.FolderOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No repositories currently installed.", fontWeight = FontWeight.Bold)
                                Text("Select 'Presets' tab above and tap 'Enable' to add extensions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(filteredInstalled, key = { it.url }) { repo ->
                        InstalledRepoCardItem(
                            repo = repo,
                            onSync = { viewModel.syncRepository(repo.url) },
                            onDelete = { viewModel.removeRepository(repo) },
                            onCopyUrl = {
                                clipboardManager.setText(AnnotatedString(repo.url))
                                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // TAB 2: SYNC STATUS & DIAGNOSTICS
            if (selectedTab == 2) {
                if (syncResults.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("No sync results in memory yet.", fontWeight = FontWeight.Bold)
                                Button(onClick = { viewModel.syncAllRepositories() }) {
                                    Text("Run Full Repository Sync Now")
                                }
                            }
                        }
                    }
                } else {
                    items(syncResults, key = { it.repoUrl }) { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (res.error == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = res.repoName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Badge(
                                        containerColor = if (res.error == null) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = if (res.error == null) "${res.plugins.size} Plugins" else "Error",
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Text(
                                    text = res.repoUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (res.error != null) {
                                    Text(
                                        text = "Error: ${res.error}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ADD CUSTOM REPO DIALOG
    if (showAddCustomDialog) {
        var urlInput by remember { mutableStateOf("") }
        var nameInput by remember { mutableStateOf("") }
        var descInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text("Add Custom Repository", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the raw URL to the repository's repo.json or plugins.json file.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Repository URL *") },
                        placeholder = { Text("https://.../repo.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            viewModel.addCustomRepository(urlInput, nameInput.ifBlank { null }, descInput.ifBlank { null })
                            showAddCustomDialog = false
                        } else {
                            Toast.makeText(context, "URL is required", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // IMPORT / EXPORT JSON DIALOG
    if (showImportExportDialog) {
        AlertDialog(
            onDismissRequest = { showImportExportDialog = false },
            title = { Text("Import / Export Repositories", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Copy JSON below to backup, or paste a JSON array of repositories to import.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        OutlinedTextField(
                            value = importExportJsonText,
                            onValueChange = { importExportJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.importJson(importExportJsonText)
                            showImportExportDialog = false
                        }
                    }
                ) {
                    Text("Import JSON")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(importExportJsonText))
                            Toast.makeText(context, "Copied JSON to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copy")
                    }
                    TextButton(onClick = { showImportExportDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun MegaRepoBannerCard(
    isInstalling: Boolean,
    onInstallClick: () -> Unit,
    megaRepoResult: xyz.mpv.rex.cinehub.extension.model.MegaRepoInstallResult?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "MegaRepo Master Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Automated CloudStream Multi-Repo Index",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("Recommended", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                }
            }

            Text(
                text = "Import and sync all top verified CloudStream extension feeds in one tap, including Hindi, Anime, English, IPTV, and high-speed streaming sources.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Button(
                onClick = onInstallClick,
                enabled = !isInstalling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Installing MegaRepo & Community Feeds...")
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install MegaRepo (One-Tap Setup)", fontWeight = FontWeight.Bold)
                }
            }

            if (megaRepoResult != null) {
                Text(
                    text = "Status: ${megaRepoResult.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (megaRepoResult.isSuccess) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PresetCardItem(
    preset: RepoPresetItem,
    isInstalled: Boolean,
    verification: RepoVerificationResult?,
    onEnable: () -> Unit,
    onVerify: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Author: ${preset.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isInstalled) {
                    Badge(containerColor = Color(0xFF2E7D32)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Installed", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            // URL Row
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = preset.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCopyUrl,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Connectivity Verification Status
            if (verification != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (verification.isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = if (verification.isOnline) {
                            "Online • HTTP ${verification.httpCode} • ${verification.latencyMs}ms (${verification.pluginCount} plugins listed)"
                        } else {
                            "Offline: ${verification.error ?: "HTTP ${verification.httpCode}"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (verification.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onVerify,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check Connection", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onEnable,
                    enabled = !isInstalled,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInstalled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isInstalled) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isInstalled) "Enabled" else "Enable", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun InstalledRepoCardItem(
    repo: ExtensionRepo,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!repo.description.isNullOrBlank()) {
                        Text(
                            text = repo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Repo",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = repo.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCopyUrl,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(14.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSync,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Manifest", fontSize = 11.sp)
                }
            }
        }
    }
}

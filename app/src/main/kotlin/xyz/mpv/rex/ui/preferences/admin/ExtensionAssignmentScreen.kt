package xyz.mpv.rex.ui.preferences.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.cinehub.provider.server.model.FirestoreExtension
import xyz.mpv.rex.cinehub.provider.server.model.PlanConfig
import xyz.mpv.rex.ui.components.glass.*
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExtensionAssignmentScreen(
    onNavigateBack: () -> Unit,
    syncService: FirebaseProviderSyncService = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    var extensions by remember { mutableStateOf<List<FirestoreExtension>>(emptyList()) }
    var plans by remember { mutableStateOf<List<PlanConfig>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLangFilter by remember { mutableStateOf<String?>(null) }
    var selectedExtensionForDetails by remember { mutableStateOf<FirestoreExtension?>(null) }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val fetchedPlans = syncService.fetchAllPlans()
            val fetchedExts = syncService.fetchAllAvailableExtensions()
            withContext(Dispatchers.Main) {
                plans = fetchedPlans
                extensions = fetchedExts
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val availableLangs = remember(extensions) {
        extensions.map { it.lang.lowercase(Locale.ROOT) }.distinct().filter { it.isNotBlank() }
    }

    val filteredExtensions = remember(extensions, searchQuery, selectedLangFilter) {
        extensions.filter { ext ->
            val matchQuery = searchQuery.isBlank() ||
                    ext.name.contains(searchQuery, ignoreCase = true) ||
                    ext.internalName.contains(searchQuery, ignoreCase = true) ||
                    ext.repository.contains(searchQuery, ignoreCase = true)
            val matchLang = selectedLangFilter == null || ext.lang.equals(selectedLangFilter, ignoreCase = true)
            matchQuery && matchLang
        }
    }

    Scaffold(
        containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
        topBar = {
            GlassTopBar(
                title = "Extension Assignment",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaxStreamTheme.CrimsonAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    // Header Card
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ChecklistRtl,
                                contentDescription = null,
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Extension Assignment Matrix",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Quickly assign or unassign individual extensions across all plans in real time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    // Search & Language Filter
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, repository, or internal ID...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        if (availableLangs.size > 1) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedLangFilter == null,
                                    onClick = { selectedLangFilter = null },
                                    label = { Text("All (${extensions.size})") }
                                )
                                availableLangs.forEach { lang ->
                                    val count = extensions.count { it.lang.equals(lang, ignoreCase = true) }
                                    FilterChip(
                                        selected = selectedLangFilter.equals(lang, ignoreCase = true),
                                        onClick = {
                                            selectedLangFilter = if (selectedLangFilter.equals(lang, ignoreCase = true)) null else lang
                                        },
                                        label = { Text("${lang.uppercase(Locale.ROOT)} ($count)") }
                                    )
                                }
                            }
                        }
                    }
                }

                items(filteredExtensions, key = { it.internalName.ifBlank { it.id } }) { ext ->
                    val extKey = ext.internalName.lowercase(Locale.ROOT)
                    val assignedPlans = plans.filter { plan ->
                        plan.allowAllExtensions || plan.allowedExtensions.any { it.equals(extKey, ignoreCase = true) }
                    }

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedExtensionForDetails = ext },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = ext.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                    )

                                    // Language Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = ext.lang.uppercase(Locale.ROOT),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Version badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v${ext.versionCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "Source: ${ext.repository.ifBlank { "CloudStream Community" }} • ID: ${ext.internalName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Plan Assignment Toggles / Chips
                            Text(
                                text = "Assigned Plans:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                plans.forEach { plan ->
                                    val isAllowed = plan.allowAllExtensions || plan.allowedExtensions.any { it.equals(extKey, ignoreCase = true) }
                                    val isLockedAll = plan.allowAllExtensions

                                    FilterChip(
                                        selected = isAllowed,
                                        enabled = !isLockedAll,
                                        onClick = {
                                            if (!isLockedAll) {
                                                scope.launch(Dispatchers.IO) {
                                                    val ok = syncService.toggleExtensionForPlan(extKey, plan.id, !isAllowed)
                                                    withContext(Dispatchers.Main) {
                                                        if (ok) {
                                                            Toast.makeText(context, "${if (!isAllowed) "Added" else "Removed"} ${ext.name} to ${plan.name}", Toast.LENGTH_SHORT).show()
                                                            loadData()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                if (isLockedAll) "${plan.name} (All Access)"
                                                else plan.name
                                            )
                                        },
                                        leadingIcon = if (isAllowed) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedExtensionForDetails?.let { ext ->
        AlertDialog(
            onDismissRequest = { selectedExtensionForDetails = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(ext.name)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(ext.lang.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Internal ID: ${ext.internalName}", style = MaterialTheme.typography.bodySmall)
                    Text("• Version: ${ext.version} (${ext.versionCode})", style = MaterialTheme.typography.bodySmall)
                    Text("• Repository: ${ext.repository}", style = MaterialTheme.typography.bodySmall)
                    if (ext.url.isNotBlank()) {
                        Text("• Package URL: ${ext.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (ext.description != null && ext.description.isNotBlank()) {
                        Text("• Description: ${ext.description}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedExtensionForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

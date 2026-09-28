package xyz.mpv.rex.ui.preferences.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
fun PlansManagementScreen(
    onNavigateBack: () -> Unit,
    syncService: FirebaseProviderSyncService = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    var plans by remember { mutableStateOf<List<PlanConfig>>(emptyList()) }
    var allExtensions by remember { mutableStateOf<List<FirestoreExtension>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editingPlan by remember { mutableStateOf<PlanConfig?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val fetchedPlans = syncService.fetchAllPlans()
            val fetchedExts = syncService.fetchAllAvailableExtensions()
            withContext(Dispatchers.Main) {
                plans = fetchedPlans
                allExtensions = fetchedExts
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // Helper map of internalName -> friendly Display Name
    val extensionNameMap = remember(allExtensions) {
        allExtensions.associate { it.internalName.lowercase(Locale.ROOT) to it.name }
    }

    Scaffold(
        containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
        topBar = {
            GlassTopBar(
                title = "Plans Management",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Plan", tint = MaxStreamTheme.CrimsonAccent)
                    }
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Header card
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
                                Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Server-Side Extension Plans",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Assign exact extension IDs to user tiers (Free, Premium, VIP, Admin). Users will only install permitted extensions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(plans, key = { it.id }) { plan ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingPlan = plan },
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
                                        text = plan.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    // Plan ID Tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = plan.id.uppercase(Locale.ROOT),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (plan.allowAllExtensions) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "ALL EXTENSIONS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaxStreamTheme.CrimsonAccent
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${plan.allowedExtensions.size} Extensions",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (plan.description.isNotBlank()) {
                                Text(
                                    text = plan.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Assigned Extension Chips
                            if (plan.allowAllExtensions) {
                                Text(
                                    text = "Grants unrestricted access to all ${allExtensions.size} available extensions in the system.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (plan.allowedExtensions.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    plan.allowedExtensions.take(12).forEach { extId ->
                                        val friendlyName = extensionNameMap[extId.lowercase(Locale.ROOT)] ?: extId
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .border(
                                                    0.5.dp,
                                                    MaxStreamTheme.GlassBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = friendlyName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (plan.allowedExtensions.size > 12) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "+${plan.allowedExtensions.size - 12} more",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "No extensions assigned yet. Tap to configure.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { editingPlan = plan }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Configure")
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val defaults = listOf("free", "premium", "vip", "admin").map { PlanConfig.defaultForPlan(it) }
                                for (d in defaults) {
                                    syncService.savePlan(d)
                                }
                                loadData()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Reset default plans in Firestore", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Seed / Reset Default Plans in Firestore")
                    }
                }
            }
        }
    }

    // Edit Plan Dialog
    editingPlan?.let { plan ->
        PlanEditorDialog(
            plan = plan,
            allExtensions = allExtensions,
            onDismiss = { editingPlan = null },
            onSave = { updatedPlan ->
                scope.launch(Dispatchers.IO) {
                    val ok = syncService.savePlan(updatedPlan)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            Toast.makeText(context, "Saved plan ${updatedPlan.name}", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(context, "Failed saving plan", Toast.LENGTH_SHORT).show()
                        }
                        editingPlan = null
                    }
                }
            },
            onDelete = { planId ->
                scope.launch(Dispatchers.IO) {
                    val ok = syncService.deletePlan(planId)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            Toast.makeText(context, "Deleted plan $planId", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(context, "Cannot delete core plan", Toast.LENGTH_SHORT).show()
                        }
                        editingPlan = null
                    }
                }
            }
        )
    }

    // Create New Plan Dialog
    if (showCreateDialog) {
        var newId by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newId,
                        onValueChange = { newId = it.lowercase(Locale.ROOT).replace(" ", "_") },
                        label = { Text("Plan ID (e.g. enterprise, tester)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Plan Name (e.g. Enterprise)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newId.isNotBlank() && newName.isNotBlank()) {
                            val newPlan = PlanConfig(
                                id = newId.trim(),
                                name = newName.trim(),
                                description = newDesc.trim(),
                                allowedExtensions = emptyList(),
                                allowAllExtensions = false
                            )
                            scope.launch(Dispatchers.IO) {
                                syncService.savePlan(newPlan)
                                withContext(Dispatchers.Main) {
                                    showCreateDialog = false
                                    loadData()
                                    editingPlan = newPlan
                                }
                            }
                        }
                    }
                ) {
                    Text("Create & Configure")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditorDialog(
    plan: PlanConfig,
    allExtensions: List<FirestoreExtension>,
    onDismiss: () -> Unit,
    onSave: (PlanConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(plan.name) }
    var description by remember { mutableStateOf(plan.description) }
    var allowAll by remember { mutableStateOf(plan.allowAllExtensions) }
    var selectedExtensions by remember { mutableStateOf(plan.allowedExtensions.map { it.lowercase(Locale.ROOT) }.toSet()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredExtensions = remember(allExtensions, searchQuery) {
        if (searchQuery.isBlank()) allExtensions
        else allExtensions.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.internalName.contains(searchQuery, ignoreCase = true) ||
            it.lang.contains(searchQuery, ignoreCase = true) ||
            it.repository.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Plan: ${plan.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Plan Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Plan Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Allow All Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Allow All Extensions",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Users on this plan automatically receive every extension in the system",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowAll,
                            onCheckedChange = { allowAll = it }
                        )
                    }

                    if (!allowAll) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Assigned Extensions (${selectedExtensions.size} selected)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search extensions...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    selectedExtensions = allExtensions.map { it.internalName.lowercase(Locale.ROOT) }.toSet()
                                }
                            ) {
                                Text("Select All")
                            }
                            TextButton(
                                onClick = {
                                    selectedExtensions = emptySet()
                                }
                            ) {
                                Text("Deselect All")
                            }
                        }

                        filteredExtensions.forEach { ext ->
                            val extKey = ext.internalName.lowercase(Locale.ROOT)
                            val isSelected = selectedExtensions.contains(extKey)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .clickable {
                                        selectedExtensions = if (isSelected) {
                                            selectedExtensions - extKey
                                        } else {
                                            selectedExtensions + extKey
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = ext.name,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        // Language Tag
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = ext.lang.uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${ext.repository.ifBlank { "CloudStream" }} • v${ext.versionCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedExtensions = if (checked) {
                                            selectedExtensions + extKey
                                        } else {
                                            selectedExtensions - extKey
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (plan.id != "free" && plan.id != "admin") {
                        TextButton(
                            onClick = { onDelete(plan.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val updated = plan.copy(
                                    name = name.trim(),
                                    description = description.trim(),
                                    allowAllExtensions = allowAll,
                                    allowedExtensions = selectedExtensions.toList()
                                )
                                onSave(updated)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                        ) {
                            Text("Save Plan")
                        }
                    }
                }
            }
        }
    }
}

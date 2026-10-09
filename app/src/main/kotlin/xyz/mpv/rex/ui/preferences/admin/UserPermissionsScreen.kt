package xyz.mpv.rex.ui.preferences.admin

import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.auth.model.UserProfile
import xyz.mpv.rex.auth.model.UserRole
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.cinehub.provider.server.model.FirestoreExtension
import xyz.mpv.rex.cinehub.provider.server.model.PlanConfig
import xyz.mpv.rex.cinehub.provider.server.model.UserPermissions
import xyz.mpv.rex.ui.components.glass.*
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserPermissionsScreen(
    onNavigateBack: () -> Unit,
    syncService: FirebaseProviderSyncService = koinInject(),
    firestore: FirebaseFirestore = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    var userList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var plans by remember { mutableStateOf<List<PlanConfig>>(emptyList()) }
    var allExtensions by remember { mutableStateOf<List<FirestoreExtension>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var manualUidInput by remember { mutableStateOf("") }

    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var userPermissions by remember { mutableStateOf<UserPermissions?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val fetchedPlans = syncService.fetchAllPlans()
            val fetchedExts = syncService.fetchAllAvailableExtensions()

            // Fetch users from Firestore
            val users = mutableListOf<UserProfile>()
            try {
                val usersSnap = firestore.collection(FirebaseProviderSyncService.USERS_COLLECTION).get().await()
                if (usersSnap != null && !usersSnap.isEmpty) {
                    for (doc in usersSnap.documents) {
                        users.add(UserProfile.fromSnapshot(doc))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("UserPermissions", "Error fetching users: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                plans = fetchedPlans
                allExtensions = fetchedExts
                userList = users
                isLoading = false
            }
        }
    }

    fun selectUser(user: UserProfile) {
        selectedUser = user
        scope.launch(Dispatchers.IO) {
            val perms = syncService.fetchUserPermissions(user.uid)
            withContext(Dispatchers.Main) {
                userPermissions = perms
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val extensionNameMap = remember(allExtensions) {
        allExtensions.associate { it.internalName.lowercase(Locale.ROOT) to it.name }
    }

    val filteredUsers = remember(userList, searchQuery) {
        if (searchQuery.isBlank()) userList
        else userList.filter {
            (it.name?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.email?.contains(searchQuery, ignoreCase = true) == true) ||
            it.uid.contains(searchQuery, ignoreCase = true) ||
            it.role.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
        topBar = {
            GlassTopBar(
                title = "User Permissions",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                            )
                        }
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = "GitHub Repository", tint = MaterialTheme.colorScheme.onSurface)
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
                    // Explanatory Banner
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
                                Icons.Outlined.ManageAccounts,
                                contentDescription = null,
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "User Tier & Extension Overrides",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Configure custom extension allowances, blacklists, and plan assignments per user.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    // Search & Direct UID lookup
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search users by name, email, or UID...") },
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
                }

                if (filteredUsers.isEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "No users found matching query",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = manualUidInput,
                                    onValueChange = { manualUidInput = it },
                                    placeholder = { Text("Enter direct Firebase UID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (manualUidInput.isNotBlank()) {
                                            selectUser(
                                                UserProfile(
                                                    uid = manualUidInput.trim(),
                                                    name = "Manual UID Target",
                                                    email = "",
                                                    role = UserRole.USER
                                                )
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                                ) {
                                    Text("Open User by UID")
                                }
                            }
                        }
                    }
                } else {
                    items(filteredUsers, key = { it.uid }) { user ->
                        val isCurrentSelected = selectedUser?.uid == user.uid
                        val isAdminRole = user.role.equals(UserRole.ADMIN, ignoreCase = true) || user.role.equals(UserRole.SUPER_ADMIN, ignoreCase = true)

                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectUser(user) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // User Avatar / Image
                                if (!user.photo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user.photo,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(
                                                1.5.dp,
                                                if (isAdminRole) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isAdminRole) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isAdminRole) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isAdminRole) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name?.takeIf { it.isNotBlank() } ?: "User (${user.uid.take(8)})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    val emailText = user.email
                                    if (!emailText.isNullOrBlank()) {
                                        Text(
                                            text = emailText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "UID: ${user.uid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isAdminRole) MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = user.role.uppercase(Locale.ROOT),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAdminRole) MaxStreamTheme.CrimsonAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isCurrentSelected) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Editing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaxStreamTheme.CrimsonAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive User Permissions Editor Dialog
    if (selectedUser != null && userPermissions != null) {
        val user = selectedUser!!
        val perms = userPermissions!!

        UserPermissionsEditorDialog(
            user = user,
            permissions = perms,
            plans = plans,
            allExtensions = allExtensions,
            extensionNameMap = extensionNameMap,
            onDismiss = {
                selectedUser = null
                userPermissions = null
            },
            onSave = { updatedPerms, onResult ->
                scope.launch(Dispatchers.IO) {
                    val ok = syncService.saveUserPermissions(user.uid, updatedPerms)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            Toast.makeText(context, "Permissions saved for ${user.name ?: user.uid} in Firebase", Toast.LENGTH_SHORT).show()
                            onResult(true, null)
                            selectedUser = null
                            userPermissions = null
                            loadData()
                        } else {
                            Toast.makeText(context, "Failed saving user permissions to Firebase", Toast.LENGTH_LONG).show()
                            onResult(false, "Failed to save permissions to Firestore. Check connection.")
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UserPermissionsEditorDialog(
    user: UserProfile,
    permissions: UserPermissions,
    plans: List<PlanConfig>,
    allExtensions: List<FirestoreExtension>,
    extensionNameMap: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (UserPermissions, (Boolean, String?) -> Unit) -> Unit
) {
    var selectedPlanId by remember { mutableStateOf(permissions.plan.ifBlank { "free" }) }
    var isAccountEnabled by remember { mutableStateOf(permissions.enabled) }
    var customExtensions by remember { mutableStateOf(permissions.customExtensions.map { it.lowercase(Locale.ROOT) }.toMutableList()) }
    var blockedExtensions by remember { mutableStateOf(permissions.blockedExtensions.map { it.lowercase(Locale.ROOT) }.toMutableList()) }

    var showAddCustomDialog by remember { mutableStateOf(false) }
    var showAddBlockedDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Calculate final resolved extensions for live preview
    val selectedPlan = plans.firstOrNull { it.id.equals(selectedPlanId, ignoreCase = true) } ?: PlanConfig.defaultForPlan(selectedPlanId)
    val previewResolved = remember(selectedPlan, customExtensions, blockedExtensions) {
        if (selectedPlan.allowAllExtensions) {
            allExtensions.map { it.internalName.lowercase(Locale.ROOT) }.toSet() - blockedExtensions.toSet()
        } else {
            (selectedPlan.allowedExtensions.map { it.lowercase(Locale.ROOT) } + customExtensions).toSet() - blockedExtensions.toSet()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!user.photo.isNullOrBlank()) {
                            AsyncImage(
                                model = user.photo,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Edit User Permissions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val emailOrUid = user.email?.takeIf { it.isNotBlank() } ?: user.uid.take(12)
                            Text(
                                text = "${user.name ?: "User"} ($emailOrUid)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Account Enabled Toggle
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
                            Text("Account Provider Access", fontWeight = FontWeight.Bold)
                            Text(
                                if (isAccountEnabled) "Enabled: extensions sync normally" else "Disabled: user blocked from extensions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAccountEnabled,
                            onCheckedChange = { isAccountEnabled = it }
                        )
                    }

                    // Plan Selector
                    Text("Assigned Plan Tier", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plans.forEach { plan ->
                            val isSelected = selectedPlanId.equals(plan.id, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPlanId = plan.id },
                                label = { Text(plan.name) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    // Custom Extensions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Custom Extensions (+)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Grant extra extensions directly to this user", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showAddCustomDialog = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Custom", tint = MaxStreamTheme.CrimsonAccent)
                        }
                    }

                    if (customExtensions.isEmpty()) {
                        Text("No custom extensions granted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            customExtensions.forEach { extId ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(extensionNameMap[extId] ?: extId) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    customExtensions = (customExtensions - extId).toMutableList()
                                                }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Blocked Extensions Blacklist
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Blocked Extensions (-)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            Text("Blacklist extensions from ever installing for this user", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showAddBlockedDialog = true }) {
                            Icon(Icons.Default.Block, contentDescription = "Block Extension", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (blockedExtensions.isEmpty()) {
                        Text("No blocked extensions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            blockedExtensions.forEach { extId ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(extensionNameMap[extId] ?: extId) },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ),
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Unblock",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    blockedExtensions = (blockedExtensions - extId).toMutableList()
                                                }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Final Resolved Extensions Preview
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Resolved Final Extensions (${previewResolved.size})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "= Plan (${selectedPlan.name}) + Custom (${customExtensions.size}) - Blocked (${blockedExtensions.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                previewResolved.take(15).forEach { extId ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = extensionNameMap[extId] ?: extId,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (previewResolved.size > 15) {
                                    Text("+${previewResolved.size - 15} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = !isSaving, onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            errorMessage = null
                            val updated = permissions.copy(
                                plan = selectedPlanId.trim().lowercase(Locale.ROOT),
                                enabled = isAccountEnabled,
                                customExtensions = customExtensions.distinct(),
                                blockedExtensions = blockedExtensions.distinct()
                            )
                            onSave(updated) { success, error ->
                                isSaving = false
                                if (!success) {
                                    errorMessage = error ?: "Failed to save permissions in Firebase."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...")
                        } else {
                            Text("Save Permissions")
                        }
                    }
                }
            }
        }
    }

    // Dialog for adding custom extensions
    if (showAddCustomDialog) {
        ExtensionPickerSheet(
            title = "Grant Custom Extension",
            allExtensions = allExtensions,
            currentSelection = customExtensions,
            onDismiss = { showAddCustomDialog = false },
            onSelect = { selectedExtId ->
                if (!customExtensions.contains(selectedExtId)) {
                    customExtensions = (customExtensions + selectedExtId).toMutableList()
                    blockedExtensions = (blockedExtensions - selectedExtId).toMutableList()
                }
                showAddCustomDialog = false
            }
        )
    }

    // Dialog for adding blocked extensions
    if (showAddBlockedDialog) {
        ExtensionPickerSheet(
            title = "Block Extension",
            allExtensions = allExtensions,
            currentSelection = blockedExtensions,
            onDismiss = { showAddBlockedDialog = false },
            onSelect = { selectedExtId ->
                if (!blockedExtensions.contains(selectedExtId)) {
                    blockedExtensions = (blockedExtensions + selectedExtId).toMutableList()
                    customExtensions = (customExtensions - selectedExtId).toMutableList()
                }
                showAddBlockedDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionPickerSheet(
    title: String,
    allExtensions: List<FirestoreExtension>,
    currentSelection: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(allExtensions, query) {
        if (query.isBlank()) allExtensions
        else allExtensions.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.internalName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search extension...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { ext ->
                        val key = ext.internalName.lowercase(Locale.ROOT)
                        val isAlreadySelected = currentSelection.contains(key)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onSelect(key) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(ext.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${ext.lang.uppercase(Locale.ROOT)} • v${ext.versionCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (isAlreadySelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaxStreamTheme.CrimsonAccent)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

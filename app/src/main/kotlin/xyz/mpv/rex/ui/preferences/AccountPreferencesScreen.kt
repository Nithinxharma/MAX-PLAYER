package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.elevation.AdminSessionManager
import xyz.mpv.rex.auth.elevation.ElevationResult
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.auth.LoginScreen
import xyz.mpv.rex.ui.components.glass.GlassButtonVariant
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Root Category 1: Account & Profile Management Screen
 *
 * Provides dedicated controls for User Profile, Membership Plan Tier,
 * Cloud Synchronization, Security Elevation, and Session Management.
 */
@Serializable
object AccountPreferencesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val isDark = isSystemInDarkTheme()
        val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current

        val authManager = koinInject<AuthManager>()
        val syncService = koinInject<FirebaseProviderSyncService>()
        val adminSessionManager = koinInject<AdminSessionManager>()

        val userProfile by authManager.userProfile.collectAsState()
        val userRole by authManager.userRole.collectAsState()
        val isAdmin by authManager.isAdmin.collectAsState()
        val isPremium by authManager.isPremium.collectAsState()
        val isElevated by adminSessionManager.isElevated.collectAsState()
        val sessionInfo by adminSessionManager.sessionInfo.collectAsState()

        val userPlan by syncService.userPlan.collectAsState()
        val permissions by syncService.userPermissions.collectAsState()
        val isSyncing by syncService.isSyncing.collectAsState()
        val syncStatus by syncService.syncStatus.collectAsState()
        val lastSyncTime by syncService.lastSyncTimeFormatted.collectAsState()
        val loadedProvidersCount by syncService.loadedProvidersCount.collectAsState()
        val installedExtensionsCount by syncService.installedExtensionsCount.collectAsState()
        val syncedRepositoriesCount by syncService.syncedRepositoriesCount.collectAsState()

        val currentUser = authManager.currentUser

        // Role evaluation
        val effectiveRole = when {
            isAdmin -> "admin"
            userRole.equals("vip", ignoreCase = true) || userPlan.equals("vip", ignoreCase = true) -> "vip"
            isPremium || userRole.equals("premium", ignoreCase = true) || userPlan.equals("premium", ignoreCase = true) -> "premium"
            else -> "user"
        }

        val roleColor = when (effectiveRole) {
            "admin" -> Color(0xFFFF2D55)
            "vip" -> Color(0xFFFFB800)
            "premium" -> Color(0xFF00E676)
            else -> Color(0xFF00C2FF)
        }

        val displayName = userProfile?.name?.takeIf { it.isNotBlank() }
            ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Max Stream User"
        val email = userProfile?.email?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.takeIf { it.isNotBlank() }
            ?: "Guest User"
        val photoUrl = userProfile?.photo?.takeIf { it.isNotBlank() }
            ?: currentUser?.photoUrl?.toString()

        var showEditNameDialog by remember { mutableStateOf(false) }
        var newNameInput by remember { mutableStateOf(displayName) }
        var showElevationDialog by remember { mutableStateOf(false) }
        var showFirebaseDiagDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = "Account & Profile",
                    onBackClick = { backstack.removeLastOrNull() },
                    actions = {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = "GitHub Repository",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            containerColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background
        ) { padding ->
            ProvidePreferenceLocals {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(top = 8.dp, bottom = navBarHeight + 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Profile Header Card
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .background(roleColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = roleColor,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = roleColor.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, roleColor.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = effectiveRole.uppercase(),
                                            color = roleColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MaxStreamGlassButton(
                                        text = "Edit Profile",
                                        variant = GlassButtonVariant.Secondary,
                                        onClick = {
                                            newNameInput = displayName
                                            showEditNameDialog = true
                                        }
                                    )

                                    if (currentUser != null) {
                                        MaxStreamGlassButton(
                                            text = "Copy UID",
                                            variant = GlassButtonVariant.Ghost,
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(currentUser.uid))
                                                Toast.makeText(context, "UID copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Plan & Entitlements Section
                    item {
                        GlassCategoryHeader(title = "Membership & Entitlements", icon = Icons.Default.WorkspacePremium)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "Active Plan Tier: ${userPlan.uppercase()}",
                                subtitle = when (effectiveRole) {
                                    "admin" -> "Full unrestricted access to all providers and administrative tools."
                                    "vip" -> "VIP 4K HDR Streaming with unrestricted provider and repository feeds."
                                    "premium" -> "High-definition streaming enabled with VIP provider access."
                                    else -> "Standard streaming access with server-managed providers."
                                },
                                icon = when (effectiveRole) {
                                    "admin" -> Icons.Default.AdminPanelSettings
                                    "vip" -> Icons.Default.WorkspacePremium
                                    "premium" -> Icons.Default.Star
                                    else -> Icons.Default.CheckCircle
                                },
                                badge = effectiveRole.uppercase(),
                                showDivider = true,
                                onClick = {
                                    Toast.makeText(context, "Current Plan: $userPlan ($effectiveRole)", Toast.LENGTH_SHORT).show()
                                }
                            )

                            GlassPreferenceItem(
                                title = "User Permissions Model",
                                subtitle = permissions?.let { "Role-based server entitlements active" } ?: "Server-managed permissions active",
                                icon = Icons.Outlined.Security,
                                showDivider = false,
                                onClick = { showFirebaseDiagDialog = true }
                            )
                        }
                    }

                    // Cloud Provider Sync Section
                    item {
                        GlassCategoryHeader(title = "Cloud Provider Sync", icon = Icons.Outlined.Sync)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "Sync Status",
                                subtitle = "$syncStatus • Loaded: $loadedProvidersCount Providers • Repos: $syncedRepositoriesCount",
                                icon = Icons.Outlined.CloudQueue,
                                badge = if (isSyncing) "SYNCING" else "ONLINE",
                                showDivider = true,
                                onClick = {
                                    Toast.makeText(context, "Last Sync: $lastSyncTime", Toast.LENGTH_SHORT).show()
                                }
                            )

                            GlassPreferenceItem(
                                title = "Force Cloud Sync",
                                subtitle = if (isSyncing) "Synchronizing..." else "Fetch latest providers, repositories, and permissions",
                                icon = Icons.Outlined.Refresh,
                                showDivider = true,
                                onClick = {
                                    scope.launch {
                                        Toast.makeText(context, "Synchronizing...", Toast.LENGTH_SHORT).show()
                                        val ok = syncService.forceSync()
                                        Toast.makeText(
                                            context,
                                            if (ok) "Sync completed successfully" else "Sync completed with notes",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )

                            GlassPreferenceItem(
                                title = "Clear Provider Cache",
                                subtitle = "Flush local manifest cache and reload provider registry",
                                icon = Icons.Outlined.CleaningServices,
                                showDivider = false,
                                onClick = {
                                    scope.launch {
                                        syncService.clearProviderCache()
                                        Toast.makeText(context, "Provider cache cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    // Security & Elevation (Admin/Developer)
                    if (isAdmin) {
                        item {
                            GlassCategoryHeader(title = "Administrative Elevation", icon = Icons.Outlined.Lock)
                            GlassSettingsSection {
                                GlassPreferenceItem(
                                    title = if (isElevated) "Admin Session: ELEVATED" else "Request Admin Elevation",
                                    subtitle = if (isElevated) {
                                        val remaining = (sessionInfo.remainingTimeMs / 60000).coerceAtLeast(1)
                                        "Elevation active ($remaining min remaining)"
                                    } else {
                                        "Elevate session to access developer configurations and diagnostic tools"
                                    },
                                    icon = Icons.Default.Key,
                                    badge = if (isElevated) "ACTIVE" else "LOCKED",
                                    showDivider = false,
                                    onClick = {
                                        if (isElevated) {
                                            val remaining = (sessionInfo.remainingTimeMs / 60000).coerceAtLeast(1)
                                            Toast.makeText(context, "Admin Session Active ($remaining min remaining)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showElevationDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Session & Authentication Actions
                    item {
                        GlassCategoryHeader(title = "Session", icon = Icons.Default.Logout)
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "Switch Account",
                                subtitle = "Sign in with another Google or Firebase account",
                                icon = Icons.Outlined.Person,
                                showDivider = true,
                                onClick = {
                                    backstack.add(LoginScreen)
                                }
                            )

                            GlassPreferenceItem(
                                title = "Logout Session",
                                subtitle = "Sign out from Firebase and clear cached credentials",
                                icon = Icons.Default.Logout,
                                showDivider = false,
                                onClick = {
                                    scope.launch {
                                        authManager.signOut(context)
                                        backstack.clear()
                                        backstack.add(LoginScreen)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Edit Display Name Dialog
        if (showEditNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                title = { Text("Edit Display Name") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Update your display name visible across the MAX STREAM interface:")
                        OutlinedTextField(
                            value = newNameInput,
                            onValueChange = { newNameInput = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newNameInput.isNotBlank()) {
                                scope.launch {
                                    runCatching {
                                        val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                            .setDisplayName(newNameInput.trim())
                                            .build()
                                        currentUser?.updateProfile(request)
                                        currentUser?.let { authManager.syncUserToFirestore(it) }
                                    }
                                    Toast.makeText(context, "Display name updated", Toast.LENGTH_SHORT).show()
                                    showEditNameDialog = false
                                }
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditNameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Admin Elevation Dialog
        if (showElevationDialog) {
            AlertDialog(
                onDismissRequest = { showElevationDialog = false },
                title = { Text("Request Administrative Elevation") },
                text = {
                    Column {
                        Text(
                            "Elevate your session to access developer configurations, diagnostics, and server provider controls. Elevation is strictly time-bounded (15 minutes).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Authenticated UID: ${currentUser?.uid ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uid = currentUser?.uid
                            if (uid != null) {
                                scope.launch {
                                    val result = adminSessionManager.requestElevation(uid)
                                    when (result) {
                                        is ElevationResult.Granted -> {
                                            showElevationDialog = false
                                            val minutes = result.durationMs / 60000
                                            Toast.makeText(context, "Admin Elevation Active ($minutes min TTL)", Toast.LENGTH_LONG).show()
                                        }
                                        is ElevationResult.Denied -> {
                                            Toast.makeText(context, "Elevation Denied: ${result.reason}", Toast.LENGTH_LONG).show()
                                        }
                                        is ElevationResult.Error -> {
                                            Toast.makeText(context, "Elevation Error: ${result.throwable.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Authorize Elevation")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showElevationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Firebase Diagnostics Dialog
        if (showFirebaseDiagDialog) {
            val fbDiagnostics = syncService.getFirebaseDiagnostics()
            MaxStreamGlassDialog(
                onDismissRequest = { showFirebaseDiagDialog = false },
                title = "Account Diagnostics",
                icon = Icons.Outlined.Storage,
                confirmButton = {
                    MaxStreamGlassButton(
                        text = "Close",
                        variant = GlassButtonVariant.Primary,
                        onClick = { showFirebaseDiagDialog = false }
                    )
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("• Authenticated: ${fbDiagnostics["authenticated"]}", fontWeight = FontWeight.Bold)
                    Text("• Firebase UID: ${fbDiagnostics["uid"]}", style = MaterialTheme.typography.bodySmall)
                    Text("• Account Email: ${fbDiagnostics["email"]}", style = MaterialTheme.typography.bodySmall)
                    Text("• Effective Plan Tier: ${fbDiagnostics["plan"]}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Permissions Model:", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${fbDiagnostics["permissions"]}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * ProfileScreen route alias to eliminate duplicate screens while preserving navigation compatibility.
 */
typealias ProfileScreen = AccountPreferencesScreen

package xyz.mpv.rex.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.auth.LoginScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.components.glass.GlassButtonVariant
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.components.glass.GlassCategoryHeader
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Modernized MAX STREAM Master Profile & Preferences Experience.
 *
 * Implements the Apple Settings Organization + Premium OTT Design System.
 *
 * Features:
 * - Premium OTT Profile Header with dynamic role tiers (User, Admin, Developer)
 * - Premium Account Card with subtle glassmorphism and live status indicators
 * - Quick Actions (Edit Profile, Manage Account, Downloads, Watch History, Admin Diagnostics)
 * - Global Settings Search
 * - 6 Core Root Categories:
 *   1. Account & Entitlements
 *   2. General Settings (Player, Controls, Decoders, Subtitles, Audio, Library, Integrations, Dev)
 *   3. Appearance & Theming
 *   4. Accessibility & Touch
 *   5. Privacy & Data Protection
 *   6. About MaxStream
 */
@Serializable
object PreferencesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
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

        val userPlan by syncService.userPlan.collectAsState()
        val isSyncing by syncService.isSyncing.collectAsState()
        val syncStatus by syncService.syncStatus.collectAsState()
        val loadedProvidersCount by syncService.loadedProvidersCount.collectAsState()
        val installedExtensionsCount by syncService.installedExtensionsCount.collectAsState()
        val syncedRepositoriesCount by syncService.syncedRepositoriesCount.collectAsState()

        val currentUser = authManager.currentUser

        // Supported Roles: User, Admin, Developer
        val isDeveloper = isAdmin || userRole.contains("dev", ignoreCase = true) || isElevated
        val effectiveRole = when {
            isAdmin -> "admin"
            isDeveloper -> "developer"
            userRole.equals("vip", ignoreCase = true) || userPlan.equals("vip", ignoreCase = true) -> "vip"
            isPremium || userRole.equals("premium", ignoreCase = true) || userPlan.equals("premium", ignoreCase = true) -> "premium"
            else -> "user"
        }

        val roleBadgeColor = when (effectiveRole) {
            "admin" -> Color(0xFFFF2D55)
            "developer" -> Color(0xFFAF52DE)
            "vip" -> Color(0xFFFFB800)
            "premium" -> Color(0xFF00E676)
            else -> Color(0xFF00C2FF)
        }

        val displayName = userProfile?.name?.takeIf { it.isNotBlank() }
            ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Max Stream User"
        val email = userProfile?.email?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.takeIf { it.isNotBlank() }
            ?: "Guest Session"
        val photoUrl = userProfile?.photo?.takeIf { it.isNotBlank() }
            ?: currentUser?.photoUrl?.toString()

        var showEditNameDialog by remember { mutableStateOf(false) }
        var newNameInput by remember { mutableStateOf(displayName) }

        Scaffold(
            topBar = {
                GlassTopBar(
                    title = stringResource(R.string.pref_preferences),
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Premium OTT Profile Header & Account Card
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(24.dp),
                            onClick = { backstack.add(AccountPreferencesScreen) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Profile Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "User Avatar",
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(roleBadgeColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Person,
                                                contentDescription = null,
                                                tint = roleBadgeColor,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = displayName,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = roleBadgeColor.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, roleBadgeColor.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = effectiveRole.uppercase(),
                                                    color = roleBadgeColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
                                    )
                                }

                                // Status Indicators Row (Subtle glass pills)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Plan Status Indicator
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (effectiveRole) {
                                                    "admin" -> Icons.Default.AdminPanelSettings
                                                    "developer" -> Icons.Outlined.Code
                                                    "vip" -> Icons.Default.WorkspacePremium
                                                    "premium" -> Icons.Default.Star
                                                    else -> Icons.Default.CheckCircle
                                                },
                                                contentDescription = null,
                                                tint = roleBadgeColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text("TIER", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaxStreamTheme.TextMuted)
                                                Text(userPlan.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = roleBadgeColor)
                                            }
                                        }
                                    }

                                    // Providers / Sync Status Indicator
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CloudQueue,
                                                contentDescription = null,
                                                tint = Color(0xFF00C2FF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text("PROVIDERS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaxStreamTheme.TextMuted)
                                                Text("$loadedProvidersCount ACTIVE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF00C2FF))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Quick Actions Section
                    item {
                        GlassCategoryHeader(title = "Quick Actions", icon = Icons.Outlined.Tune)
                        GlassSettingsSection {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickActionButton(
                                    label = "Edit Profile",
                                    icon = Icons.Default.Edit,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        newNameInput = displayName
                                        showEditNameDialog = true
                                    }
                                )

                                QuickActionButton(
                                    label = "Account",
                                    icon = Icons.Outlined.ManageAccounts,
                                    modifier = Modifier.weight(1f),
                                    onClick = { backstack.add(AccountPreferencesScreen) }
                                )

                                QuickActionButton(
                                    label = "Downloads",
                                    icon = Icons.Outlined.Download,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        Toast.makeText(context, "Offline Downloads Manager", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                QuickActionButton(
                                    label = "History",
                                    icon = Icons.Outlined.History,
                                    modifier = Modifier.weight(1f),
                                    onClick = { backstack.add(RecentlyPlayedScreen) }
                                )
                            }

                            // Role-Aware Quick Actions for Admin & Developer Accounts
                            if (isAdmin || isDeveloper) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickActionButton(
                                        label = "Admin Tools",
                                        icon = Icons.Outlined.Build,
                                        accentColor = Color(0xFFFF2D55),
                                        modifier = Modifier.weight(1f),
                                        onClick = { backstack.add(xyz.mpv.rex.ui.preferences.admin.PlansManagementScreenRoute) }
                                    )

                                    QuickActionButton(
                                        label = "Providers",
                                        icon = Icons.Outlined.Extension,
                                        accentColor = Color(0xFFFFB800),
                                        modifier = Modifier.weight(1f),
                                        onClick = { backstack.add(xyz.mpv.rex.ui.preferences.ExtensionRepositoriesScreenRoute) }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Global Settings Search
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(18.dp),
                            onClick = { backstack.add(SettingsSearchScreen) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search Settings",
                                    tint = MaxStreamTheme.CrimsonAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.settings_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 4. Root Categories List (Apple-style rows with Luxury OTT presentation)
                    item {
                        GlassCategoryHeader(title = "Preferences Categories", icon = Icons.Default.Settings)
                        GlassSettingsSection {
                            // Category 1: Account
                            GlassPreferenceItem(
                                title = "Account & Entitlements",
                                subtitle = "Profile details, membership tiers, and cloud sync",
                                icon = Icons.Outlined.Person,
                                badge = effectiveRole.uppercase(),
                                showDivider = true,
                                onClick = { backstack.add(AccountPreferencesScreen) }
                            )

                            // Category 2: General
                            GlassPreferenceItem(
                                title = "General Settings",
                                subtitle = "Player engine, controls, audio, decoders, IPTV, and media library",
                                icon = Icons.Outlined.Tune,
                                showDivider = true,
                                onClick = { backstack.add(GeneralPreferencesScreen) }
                            )

                            // Category 3: Appearance
                            GlassPreferenceItem(
                                title = stringResource(R.string.pref_appearance_title),
                                subtitle = "Dark theme, AMOLED mode, glassmorphism depth, and color accents",
                                icon = Icons.Outlined.Palette,
                                showDivider = true,
                                onClick = { backstack.add(AppearancePreferencesScreen) }
                            )

                            // Category 4: Accessibility
                            GlassPreferenceItem(
                                title = "Accessibility",
                                subtitle = "High contrast, large touch targets, subtitles, and haptic feedback",
                                icon = Icons.Outlined.AccessibilityNew,
                                showDivider = true,
                                onClick = { backstack.add(AccessibilityPreferencesScreen) }
                            )

                            // Category 5: Privacy
                            GlassPreferenceItem(
                                title = "Privacy & Security",
                                subtitle = "Watch history controls, search cache, telemetry, and policies",
                                icon = Icons.Outlined.Security,
                                showDivider = true,
                                onClick = { backstack.add(PrivacyPreferencesScreen) }
                            )

                            // Category 6: About MaxStream
                            GlassPreferenceItem(
                                title = stringResource(R.string.pref_about_title),
                                subtitle = "Version info, open source licenses, updates, and engine diagnostics",
                                icon = Icons.Outlined.Info,
                                showDivider = true,
                                onClick = { backstack.add(AboutScreen) }
                            )

                            // Category 7: GitHub Project
                            GlassPreferenceItem(
                                title = "GitHub Project",
                                subtitle = "NithinXharma/MAx-player • Source code, releases, and issues",
                                icon = Icons.Outlined.Code,
                                showDivider = false,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://github.com/NithinXharma/MAx-player".toUri())
                                    )
                                }
                            )
                        }
                    }

                    // 5. Session Logout
                    item {
                        GlassSettingsSection {
                            GlassPreferenceItem(
                                title = "Logout Session",
                                subtitle = "Sign out from Firebase and clear active credentials",
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

        // Edit Profile Name Dialog
        if (showEditNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                title = { Text("Edit Display Name") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Update your display name across MaxStream:")
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
    }
}

/**
 * Compact Glass Quick Action Button with subtle tactile press feedback.
 */
@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val tint = accentColor ?: (if (isDark) Color.White else MaterialTheme.colorScheme.primary)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 10.sp
            )
        }
    }
}

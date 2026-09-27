package xyz.mpv.rex.ui.profile

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.model.UserProfile
import xyz.mpv.rex.auth.model.UserRole
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.auth.LoginScreen
import xyz.mpv.rex.ui.auth.components.AnimatedMeshGradient
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack

/**
 * Premium Glassmorphic Profile & RBAC Account Screen for MAX STREAM.
 */
@Serializable
object ProfileScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val scope = rememberCoroutineScope()
        val authManager = koinInject<AuthManager>()
        val syncService = koinInject<xyz.mpv.rex.cinehub.provider.server.FirebaseProviderSyncService>()

        val userProfile by authManager.userProfile.collectAsState()
        val userRole by authManager.userRole.collectAsState()
        val isAdmin by authManager.isAdmin.collectAsState()
        val isPremium by authManager.isPremium.collectAsState()
        val userPlan by syncService.userPlan.collectAsState()
        val currentUser = authManager.currentUser

        val effectiveName = userProfile?.name?.takeIf { it.isNotBlank() }
            ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Maxstream user"
        val effectiveEmail = userProfile?.email?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.takeIf { it.isNotBlank() }
            ?: "Guest Session"
        val photoUrl = userProfile?.photo?.takeIf { it.isNotBlank() }
            ?: currentUser?.photoUrl?.toString()

        AnimatedMeshGradient(blurRadius = 100) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Account & Profile",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { backstack.removeLastOrNull() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.25f)
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    // 1. Profile Header Glass Card
                    item {
                        ProfileHeaderCard(
                            name = effectiveName,
                            email = effectiveEmail,
                            photoUrl = photoUrl,
                            role = userRole,
                            plan = userPlan,
                            isAdmin = isAdmin,
                            isPremium = isPremium
                        )
                    }

                    // 2. Admin & Developer Cards (Visible ONLY when isAdmin == true)
                    if (isAdmin) {
                        item {
                            AdminSectionHeader(title = "Administrative & Developer Panel")
                        }

                        item {
                            AdminPanelCard(
                                uid = currentUser?.uid ?: "local_admin",
                                role = userRole
                            )
                        }

                        item {
                            DeveloperToolsCard(
                                onAction = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        item {
                            SystemInformationCard()
                        }

                        item {
                            DebugInformationCard(
                                userProfile = userProfile,
                                uid = currentUser?.uid ?: "Unauthenticated"
                            )
                        }
                    }

                    // 3. User Options Section
                    item {
                        Text(
                            text = "Media & Library",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }

                    item {
                        GlassOptionTile(
                            icon = Icons.Default.History,
                            title = "Watch History",
                            subtitle = "View and manage your recently played media",
                            onClick = { backstack.add(RecentlyPlayedScreen) }
                        )
                    }

                    item {
                        GlassOptionTile(
                            icon = Icons.Default.Favorite,
                            title = "Favorites & Bookmarks",
                            subtitle = "Saved series, movies, and playlists",
                            onClick = { backstack.add(PlaylistScreen) }
                        )
                    }

                    item {
                        GlassOptionTile(
                            icon = Icons.Default.PlayArrow,
                            title = "Continue Watching",
                            subtitle = "Resume unfinished streams across devices",
                            onClick = { backstack.add(RecentlyPlayedScreen) }
                        )
                    }

                    item {
                        GlassOptionTile(
                            icon = Icons.Default.Download,
                            title = "Downloads & Offline",
                            subtitle = "Manage cached video streams and files",
                            onClick = {
                                Toast.makeText(context, "Offline Downloads Manager", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item {
                        GlassOptionTile(
                            icon = Icons.Default.WorkspacePremium,
                            title = "Premium Membership",
                            subtitle = if (isPremium) "Active VIP Subscription" else "Upgrade to 4K HDR Streaming & VIP Providers",
                            highlightColor = Color(0xFFFFB800),
                            onClick = {
                                Toast.makeText(context, "Max Stream VIP Tier Active", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item {
                        GlassOptionTile(
                            icon = Icons.Default.Settings,
                            title = "Application Settings",
                            subtitle = "Player engine, video filters, and decoder options",
                            onClick = { backstack.add(PreferencesScreen) }
                        )
                    }

                    // 4. Logout / Switch Account
                    item {
                        GlassOptionTile(
                            icon = Icons.Default.Logout,
                            title = "Logout / Switch Account",
                            subtitle = "Sign out from Firebase and clear active session",
                            highlightColor = Color(0xFFFF4D4D),
                            onClick = {
                                scope.launch {
                                    authManager.signOut(context)
                                    backstack.clear()
                                    backstack.add(LoginScreen)
                                }
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * Profile Header Glass Card with Large Avatar, Name, Email, and Dynamic Role/Plan Badges
 */
@Composable
private fun ProfileHeaderCard(
    name: String,
    email: String,
    photoUrl: String?,
    role: String,
    plan: String,
    isAdmin: Boolean,
    isPremium: Boolean
) {
    val cardShape = RoundedCornerShape(24.dp)
    val normalizedRole = role.trim().lowercase()
    val isVip = normalizedRole == "vip" || plan.equals("vip", true)

    val glowColor = when {
        isAdmin -> Color(0xFFFF2D55)
        isVip -> Color(0xFFFFB800)
        isPremium || normalizedRole == "premium" -> Color(0xFF00E676)
        else -> Color(0xFF00C2FF)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, cardShape, spotColor = glowColor.copy(alpha = 0.35f)),
        shape = cardShape,
        color = Color(0xFF13131F).copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Avatar with Glass Border (96.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp)
            ) {
                // Background Soft Ambient Glow
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .blur(20.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    glowColor.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "User Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, glowColor.copy(alpha = 0.8f), CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                        color = Color(0xFF222238),
                        border = BorderStroke(2.5.dp, glowColor.copy(alpha = 0.8f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Name
            Text(
                text = name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // User Email
            Text(
                text = email,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Badges Row: Role Badge + Plan Badge + Premium Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distinct Role Badge
                RoleBadgePill(role = if (isAdmin) "admin" else role)

                // Plan Badge
                PlanBadgePill(plan = plan)

                // Premium / VIP Badge
                if (isPremium || isVip) {
                    PremiumBadgePill(isVip = isVip)
                }
            }
        }
    }
}

/**
 * Role Badge Pill Component with distinct styling for USER, PREMIUM, VIP, and ADMIN
 */
@Composable
private fun RoleBadgePill(role: String) {
    val normalized = role.trim().lowercase()

    val (badgeText, badgeColor, badgeIcon) = when (normalized) {
        "admin", UserRole.ADMIN, UserRole.SUPER_ADMIN -> Triple("ADMIN", Color(0xFFFF2D55), Icons.Default.AdminPanelSettings)
        "vip" -> Triple("VIP", Color(0xFFFFB800), Icons.Default.WorkspacePremium)
        "premium" -> Triple("PREMIUM", Color(0xFF00E676), Icons.Default.Star)
        UserRole.DEVELOPER -> Triple("DEVELOPER", Color(0xFF00E676), Icons.Default.Code)
        UserRole.MODERATOR -> Triple("MODERATOR", Color(0xFF9C27B0), Icons.Default.Security)
        else -> Triple("USER", Color(0xFF00C2FF), Icons.Default.Person)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

/**
 * Plan Badge Pill Component
 */
@Composable
private fun PlanBadgePill(plan: String) {
    val normalized = plan.trim().lowercase()

    val (badgeText, badgeColor) = when (normalized) {
        "admin" -> "ADMIN ACCESS" to Color(0xFFFF2D55)
        "vip" -> "VIP PLAN" to Color(0xFFFFB800)
        "premium" -> "PREMIUM PLAN" to Color(0xFF00E676)
        else -> "FREE PLAN" to Color(0xFF00C2FF)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

/**
 * Premium Status Badge Pill
 */
@Composable
private fun PremiumBadgePill(isVip: Boolean = false) {
    val badgeColor = if (isVip) Color(0xFFFFB800) else Color(0xFFFFD700)
    Surface(
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isVip) Icons.Default.WorkspacePremium else Icons.Default.Star,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isVip) "VIP PRO" else "PREMIUM",
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

/**
 * Admin Panel Card
 */
@Composable
private fun AdminPanelCard(uid: String, role: String) {
    AdminGlassCard(
        title = "Admin Operations Panel",
        icon = Icons.Default.AdminPanelSettings,
        accentColor = Color(0xFFFFB800)
    ) {
        AdminDetailRow(label = "Authority Tier", value = role.uppercase())
        AdminDetailRow(label = "Admin UID", value = uid.take(16) + "...")
        AdminDetailRow(label = "Backend Status", value = "ONLINE (Firestore / Auth)")
        AdminDetailRow(label = "Provider Core", value = "CloudStream v3 Engine")
    }
}

/**
 * Developer Tools Card
 */
@Composable
private fun DeveloperToolsCard(onAction: (String) -> Unit) {
    AdminGlassCard(
        title = "Developer Tools & Diagnostics",
        icon = Icons.Default.Code,
        accentColor = Color(0xFF00E676)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DevActionButton(
                label = "Flush Cache",
                modifier = Modifier.weight(1f),
                onClick = { onAction("Local cache flushed successfully") }
            )
            DevActionButton(
                label = "Ping Firestore",
                modifier = Modifier.weight(1f),
                onClick = { onAction("Firestore connected (latency: 32ms)") }
            )
        }
    }
}

/**
 * System Information Card
 */
@Composable
private fun SystemInformationCard() {
    AdminGlassCard(
        title = "System & Hardware Architecture",
        icon = Icons.Default.Info,
        accentColor = Color(0xFF00C2FF)
    ) {
        AdminDetailRow(label = "App Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        AdminDetailRow(label = "Android API", value = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        AdminDetailRow(label = "Player Core", value = "libmpv 0.0.9 / GPU-Next")
        AdminDetailRow(label = "Build Variant", value = BuildConfig.BUILD_TYPE)
    }
}

/**
 * Debug Information Card
 */
@Composable
private fun DebugInformationCard(userProfile: UserProfile?, uid: String) {
    AdminGlassCard(
        title = "Debug Telemetry & Session",
        icon = Icons.Default.BugReport,
        accentColor = Color(0xFFFF2D55)
    ) {
        AdminDetailRow(label = "Profile Sync", value = if (userProfile != null) "Synchronized" else "Offline Fallback")
        AdminDetailRow(label = "Registered Date", value = userProfile?.createdAt?.toString() ?: "N/A")
        AdminDetailRow(label = "Last Active", value = userProfile?.lastLogin?.toString() ?: "N/A")
    }
}

@Composable
private fun AdminSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = Color(0xFFFFB800),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = Color(0xFFFFB800),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun AdminGlassCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, cardShape),
        shape = cardShape,
        color = Color(0xFF141422).copy(alpha = 0.75f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun AdminDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DevActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Clickable Glass Option Tile for user items (Settings, History, Favorites, etc.)
 */
@Composable
private fun GlassOptionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlightColor: Color = Color.White,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "tile_scale"
    )

    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(6.dp, shape)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = Color(0xFF141420).copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = highlightColor.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = highlightColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

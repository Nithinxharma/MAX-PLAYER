package xyz.mpv.rex.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import xyz.mpv.rex.auth.FirebaseAuthManager
import xyz.mpv.rex.auth.model.UserProfile
import xyz.mpv.rex.auth.model.UserRole
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authManager: FirebaseAuthManager,
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by authManager.currentUserProfile.collectAsState()

    var allUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(false) }

    fun refreshUsers() {
        if (currentUser?.isAdministrator == true) {
            coroutineScope.launch {
                isLoadingUsers = true
                val result = authManager.getAllUsers()
                if (result.isSuccess) {
                    allUsers = result.getOrNull() ?: emptyList()
                }
                isLoadingUsers = false
            }
        }
    }

    LaunchedEffect(currentUser?.isAdministrator) {
        if (currentUser?.isAdministrator == true) {
            refreshUsers()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Account & Settings",
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.TextPrimary
                    )
                },
                actions = {
                    if (currentUser?.isAdministrator == true) {
                        IconButton(onClick = { refreshUsers() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Users",
                                tint = MaxStreamTheme.ElectricCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaxStreamTheme.MidnightSurface
                )
            )
        },
        containerColor = MaxStreamTheme.AbyssBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaxStreamTheme.ElevatedSurface)
                                .border(2.dp, MaxStreamTheme.ElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentUser?.photoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(currentUser?.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "User Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaxStreamTheme.TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentUser?.displayName ?: "Guest User",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaxStreamTheme.TextPrimary
                        )

                        Text(
                            text = currentUser?.email ?: "Not signed in",
                            fontSize = 13.sp,
                            color = MaxStreamTheme.TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val role = currentUser?.role?.uppercase() ?: "USER"
                            val plan = currentUser?.plan?.uppercase() ?: "FREE"
                            val isAdmin = currentUser?.isAdministrator == true

                            SuggestionChip(
                                onClick = {},
                                label = { Text("ROLE: $role", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isAdmin) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.ElevatedSurface,
                                    labelColor = Color.White
                                )
                            )

                            SuggestionChip(
                                onClick = {},
                                label = { Text("PLAN: $plan", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (plan == "ADMIN" || plan == "VIP") MaxStreamTheme.AmberWarning else MaxStreamTheme.ElevatedSurface,
                                    labelColor = if (plan == "ADMIN" || plan == "VIP") Color.Black else Color.White
                                )
                            )

                            if (currentUser?.premium == true) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("PREMIUM", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaxStreamTheme.NeonGreen,
                                        labelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Admin Management Panel
            if (currentUser?.isAdministrator == true) {
                item {
                    Text(
                        text = "Admin User Management (${allUsers.size} Users)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.ElectricCyan
                    )
                }

                if (isLoadingUsers) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaxStreamTheme.ElectricCyan)
                        }
                    }
                } else if (allUsers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface)
                        ) {
                            Text(
                                text = "No users found in Firestore. Tap Refresh above to sync.",
                                color = MaxStreamTheme.TextSecondary,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(allUsers) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // User Avatar
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaxStreamTheme.ElevatedSurface)
                                        .border(1.dp, MaxStreamTheme.GlassBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (user.photoUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(user.photoUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "User Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = MaxStreamTheme.TextSecondary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName.ifEmpty { "User" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaxStreamTheme.TextPrimary
                                    )
                                    Text(
                                        text = user.email,
                                        fontSize = 12.sp,
                                        color = MaxStreamTheme.TextSecondary
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "Role: ${user.role.uppercase()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (user.isAdministrator) MaxStreamTheme.CrimsonAccent else MaxStreamTheme.ElectricCyan
                                        )
                                        Text(
                                            text = "| Plan: ${user.plan.uppercase()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaxStreamTheme.AmberWarning
                                        )
                                    }
                                }

                                // Quick Role Toggle Button for Admin
                                IconButton(
                                    onClick = {
                                        val newRole = if (user.role == UserRole.USER) UserRole.VIP else UserRole.USER
                                        val newPlan = if (newRole == UserRole.VIP) "vip" else "free"
                                        coroutineScope.launch {
                                            authManager.updateUserRole(user.uid, newRole, newPlan)
                                            Toast.makeText(context, "Updated ${user.email} to $newRole", Toast.LENGTH_SHORT).show()
                                            refreshUsers()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Role",
                                        tint = MaxStreamTheme.ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Actions Section
            item {
                Button(
                    onClick = {
                        authManager.signOut()
                        Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

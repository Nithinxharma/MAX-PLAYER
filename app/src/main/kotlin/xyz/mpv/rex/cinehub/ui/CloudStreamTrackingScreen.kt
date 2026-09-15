package xyz.mpv.rex.cinehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.cinehub.tracking.CloudStreamTrackingManager
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object CloudStreamTrackingRoute : Screen {
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        CloudStreamTrackingScreen(
            onNavigateBack = { backstack.removeLastOrNull() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStreamTrackingScreen(
    onNavigateBack: () -> Unit
) {
    val trackingManager = koinInject<CloudStreamTrackingManager>()
    val accounts by trackingManager.accounts.collectAsState()

    var loginTargetService by remember { mutableStateOf<SyncIdName?>(null) }
    var inputUsername by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf("") }

    val supportedServices = listOf(
        SyncIdName.Trakt to "Track movies & TV shows, scrobble playback progress, and sync watchlists.",
        SyncIdName.Anilist to "Track anime watch progress, scores, and status on AniList.",
        SyncIdName.MyAnimeList to "Sync anime episodes watched and ratings with MyAnimeList.",
        SyncIdName.Simkl to "Universal tracking for movies, TV series, and anime with Simkl."
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking & Scrobbling", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("tracking_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Automated MPV Playback Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Playback in REX player automatically scrobbles to your connected accounts and marks completed at 85%.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            items(supportedServices) { (service, description) ->
                val account = accounts[service]
                val isLoggedIn = account?.isLoggedIn == true

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("tracker_card_${service.name}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isLoggedIn) Icons.Default.CheckCircle else Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = service.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isLoggedIn) "Logged in as ${account?.username ?: "User"}" else "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            if (isLoggedIn) {
                                OutlinedButton(
                                    onClick = { trackingManager.logout(service) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Logout")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        loginTargetService = service
                                        inputUsername = ""
                                        inputToken = ""
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Connect")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    loginTargetService?.let { service ->
        AlertDialog(
            onDismissRequest = { loginTargetService = null },
            title = { Text("Connect to ${service.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your account credentials or personal API token for ${service.name}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = inputUsername,
                        onValueChange = { inputUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("tracker_username_input")
                    )
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("Token / API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("tracker_token_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputToken.isNotBlank()) {
                            trackingManager.setAccount(
                                service = service,
                                username = inputUsername.ifBlank { "User" },
                                token = inputToken.trim()
                            )
                            loginTargetService = null
                        }
                    },
                    modifier = Modifier.testTag("tracker_save_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { loginTargetService = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

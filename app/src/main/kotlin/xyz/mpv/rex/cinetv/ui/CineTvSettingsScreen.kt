package xyz.mpv.rex.cinetv.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xyz.mpv.rex.cinetv.data.JioTvRepo
import xyz.mpv.rex.cinetv.model.ChannelCacheEntry
import xyz.mpv.rex.cinetv.model.LiveChannelItem
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object CineTvSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var authSuccess by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }
        var authError by remember { mutableStateOf("") }
        var isAuthenticating by remember { mutableStateOf(false) }

        var m3uEntries by remember { mutableStateOf<List<JioTvRepo.M3uEntry>>(emptyList()) }
        var smartCache by remember { mutableStateOf<Map<String, ChannelCacheEntry>>(emptyMap()) }
        var allChannels by remember { mutableStateOf<List<LiveChannelItem>>(emptyList()) }

        LaunchedEffect(Unit) {
            m3uEntries = JioTvRepo.loadM3uFallback(context)
            smartCache = JioTvRepo.getChannelCacheMap(context)
            allChannels = JioTvRepo.fetchLiveChannelsFromAssets(context)
        }

        val primaryTextColor = MaxStreamTheme.primaryTextColor
        val secondaryTextColor = MaxStreamTheme.secondaryTextColor
        val mutedTextColor = MaxStreamTheme.mutedTextColor
        val glassSurface = MaxStreamTheme.glassSurfaceColor
        val elevatedSurface = MaxStreamTheme.elevatedSurfaceColor
        val glassBorder = MaxStreamTheme.glassBorderColor
        val bgColor = MaxStreamTheme.backgroundColor

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CineTV Settings & Playlist",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = primaryTextColor
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { backstack.removeLastOrNull() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryTextColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = bgColor
                    )
                )
            },
            containerColor = bgColor
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                // Auth Session Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = elevatedSurface,
                        border = BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaxStreamTheme.CrimsonAccent,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "CineTV Live Stream Authentication",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = primaryTextColor
                            )
                            Text(
                                "Sign in with mobile number and password to enable high-bitrate adaptive live feeds.",
                                fontSize = 12.sp,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            if (authSuccess) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1B5E20).copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Active Live Session", fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                                            Text("Direct low-latency streams connected", fontSize = 11.sp, color = secondaryTextColor)
                                        }
                                        Button(
                                            onClick = {
                                                JioTvRepo.logout(context)
                                                authSuccess = false
                                                Toast.makeText(context, "Session Cleared", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                        ) {
                                            Text("Log Out", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Mobile Number / User ID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = primaryTextColor,
                                        unfocusedTextColor = primaryTextColor,
                                        focusedBorderColor = MaxStreamTheme.CrimsonAccent,
                                        unfocusedBorderColor = glassBorder,
                                        focusedLabelColor = MaxStreamTheme.CrimsonAccent,
                                        unfocusedLabelColor = secondaryTextColor
                                    )
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = primaryTextColor,
                                        unfocusedTextColor = primaryTextColor,
                                        focusedBorderColor = MaxStreamTheme.CrimsonAccent,
                                        unfocusedBorderColor = glassBorder,
                                        focusedLabelColor = MaxStreamTheme.CrimsonAccent,
                                        unfocusedLabelColor = secondaryTextColor
                                    )
                                )

                                if (authError.isNotBlank()) {
                                    Text(authError, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                }

                                Spacer(Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        isAuthenticating = true
                                        authError = ""
                                        scope.launch {
                                            try {
                                                val ok = JioTvRepo.verifyOtp(context, username, password)
                                                isAuthenticating = false
                                                if (ok) {
                                                    authSuccess = true
                                                    Toast.makeText(context, "Authentication Successful!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    authError = "Invalid credentials or token expired."
                                                }
                                            } catch (e: Exception) {
                                                isAuthenticating = false
                                                authError = e.message ?: "Authentication failed"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isAuthenticating && username.isNotBlank() && password.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isAuthenticating) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text("AUTHENTICATE", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Mapped Channels Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = elevatedSurface,
                        border = BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Mapped Channels", fontWeight = FontWeight.Bold, color = MaxStreamTheme.ElectricCyan)
                            val verifiedMappings = smartCache.values.filter { it.isManualMapping }
                            if (verifiedMappings.isEmpty()) {
                                Text("No manual mappings yet.", color = mutedTextColor, modifier = Modifier.padding(top = 8.dp))
                            } else {
                                verifiedMappings.forEach { mapping ->
                                    val channel = allChannels.find { it.defaultChannelId == mapping.channelId }
                                    if (channel != null) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = glassSurface)
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    AsyncImage(
                                                        model = channel.logoUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White)
                                                            .padding(2.dp)
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(channel.title, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                                        Text("Mapped: ${mapping.mappedM3uName ?: "Direct URL"}", fontSize = 11.sp, color = secondaryTextColor)
                                                    }
                                                }
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                    TextButton(onClick = {
                                                        JioTvRepo.resetMapping(context, mapping.channelId)
                                                        smartCache = JioTvRepo.getChannelCacheMap(context)
                                                        Toast.makeText(context, "Mapping Removed!", Toast.LENGTH_SHORT).show()
                                                    }) { Text("Delete", color = Color.Red) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // M3U Channel List
                item {
                    Text(
                        "IN.M3U Channel List (${m3uEntries.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                items(m3uEntries) { entry ->
                    var testResult by remember { mutableStateOf("") }
                    var isTesting by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = elevatedSurface)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(entry.name, fontWeight = FontWeight.Bold, color = primaryTextColor)
                            Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = secondaryTextColor, fontSize = 11.sp)
                            if (testResult.isNotBlank()) {
                                Text(
                                    "Status: $testResult",
                                    color = if (testResult == "Working") Color(0xFF00E676) else Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        isTesting = true
                                        testResult = "Testing..."
                                        scope.launch {
                                            testResult = JioTvRepo.testStreamUrl(entry.url, entry.headers)
                                            isTesting = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isTesting
                                ) {
                                    Text(if (isTesting) "..." else "TEST STREAM", color = primaryTextColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

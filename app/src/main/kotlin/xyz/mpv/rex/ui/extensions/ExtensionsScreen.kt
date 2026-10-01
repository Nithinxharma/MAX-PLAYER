package xyz.mpv.rex.ui.extensions

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinehub.extension.manager.ExtensionManager
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    extensionManager: ExtensionManager,
    onOpenTestCenter: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val activeProviders = remember { ProviderRegistry.getAll() }

    var repoUrlInput by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Extensions & Providers",
                        fontWeight = FontWeight.Bold,
                        color = MaxStreamTheme.TextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onOpenTestCenter) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "CloudStream Test Center",
                            tint = MaxStreamTheme.ElectricCyan
                        )
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
            // Install Extension Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Add Custom .cs3 Extension",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaxStreamTheme.ElectricCyan
                        )
                        Text(
                            text = "Enter direct .cs3 file URL to install and compile in runtime.",
                            fontSize = 12.sp,
                            color = MaxStreamTheme.TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                placeholder = { Text("https://example.com/provider.cs3", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaxStreamTheme.ElevatedSurface,
                                    unfocusedContainerColor = MaxStreamTheme.ElevatedSurface,
                                    focusedBorderColor = MaxStreamTheme.ElectricCyan,
                                    unfocusedBorderColor = MaxStreamTheme.GlassBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (repoUrlInput.isNotBlank()) {
                                        coroutineScope.launch {
                                            isInstalling = true
                                            val result = extensionManager.installFromUrl(
                                                repoUrlInput.trim(),
                                                "custom_ext_${System.currentTimeMillis()}"
                                            )
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "Extension installed successfully!", Toast.LENGTH_SHORT).show()
                                                repoUrlInput = ""
                                            } else {
                                                Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                            isInstalling = false
                                        }
                                    }
                                },
                                enabled = !isInstalling,
                                colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isInstalling) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                } else {
                                    Text("Install")
                                }
                            }
                        }
                    }
                }
            }

            // Test Center Callout
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.ElevatedSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.ElectricCyan)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaxStreamTheme.ElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CloudStream Test Center",
                                fontWeight = FontWeight.Bold,
                                color = MaxStreamTheme.TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Run real-time CS3 terminal commands, inspect classes, and debug search & extraction pipelines.",
                                fontSize = 12.sp,
                                color = MaxStreamTheme.TextSecondary
                            )
                        }
                        IconButton(onClick = onOpenTestCenter) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open Test Center",
                                tint = MaxStreamTheme.ElectricCyan
                            )
                        }
                    }
                }
            }

            // Active Registered Providers Section
            item {
                Text(
                    text = "Active CloudStream Providers (${activeProviders.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaxStreamTheme.TextPrimary
                )
            }

            items(activeProviders) { provider ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaxStreamTheme.MidnightSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaxStreamTheme.GlassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaxStreamTheme.ElevatedSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaxStreamTheme.GlassBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaxStreamTheme.ElectricCyan
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaxStreamTheme.TextPrimary
                            )
                            Text(
                                text = provider.mainUrl,
                                fontSize = 12.sp,
                                color = MaxStreamTheme.TextSecondary
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(provider.lang.uppercase(), fontSize = 10.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaxStreamTheme.ElevatedSurface,
                                        labelColor = MaxStreamTheme.TextPrimary
                                    )
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("ONLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaxStreamTheme.NeonGreen.copy(alpha = 0.2f),
                                        labelColor = MaxStreamTheme.NeonGreen
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

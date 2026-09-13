package xyz.mpv.rex.ui.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Lock
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xyz.mpv.rex.cinetv.data.JioTvRepo
import xyz.mpv.rex.cinetv.data.JioTvRepo.M3uEntry
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object JioTvSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val backstack = LocalBackStack.current

        var userAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }
        var m3uEntries by remember { mutableStateOf(JioTvRepo.loadM3uFallback(context)) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("JioTV Integration") },
                    navigationIcon = {
                        IconButton(onClick = { backstack.removeLastOrNull() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (userAuthed) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Authenticated Session Secure", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Button(
                                onClick = { JioTvRepo.logout(context); userAuthed = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Revoke Login Session") }
                        } else {
                            var mobile by remember { mutableStateOf("") }
                            var otpCode by remember { mutableStateOf("") }
                            var isOtpSent by remember { mutableStateOf(false) }
                            
                            OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (isOtpSent) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(value = otpCode, onValueChange = { otpCode = it }, label = { Text("Enter OTP Code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (isOtpSent) {
                                        scope.launch {
                                            if (JioTvRepo.verifyOtp(context, mobile, otpCode)) {
                                                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                                userAuthed = true
                                            } else {
                                                Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            if (JioTvRepo.requestOtp(mobile)) {
                                                isOtpSent = true
                                                Toast.makeText(context, "OTP Sent", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to send OTP", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text(if (isOtpSent) "VERIFY OTP" else "SEND OTP")
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(20.dp)) {
                        Text("Local File Mode", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            OutlinedButton(onClick = { 
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val data = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (data.contains("#EXTM3U")) {
                                    JioTvRepo.saveM3uText(context, data)
                                    JioTvRepo.reloadM3uParser()
                                    m3uEntries = JioTvRepo.loadM3uFallback(context)
                                    Toast.makeText(context, "Imported from clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No valid M3U in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Import/Replace") }
                            OutlinedButton(onClick = { 
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("M3U", JioTvRepo.readM3uText(context)))
                                Toast.makeText(context, "Exported to clipboard!", Toast.LENGTH_SHORT).show()
                            }) { Text("Export") }
                            OutlinedButton(onClick = { 
                                JioTvRepo.reloadM3uParser()
                                m3uEntries = JioTvRepo.loadM3uFallback(context)
                                Toast.makeText(context, "Parser Reloaded", Toast.LENGTH_SHORT).show()
                            }) { Text("Reload") }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    Text("IN.M3U Channel List", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                
                items(m3uEntries) { entry ->
                    var testResult by remember { mutableStateOf("") }
                    var isTesting by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.name, fontWeight = FontWeight.Bold)
                            Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray, fontSize = 10.sp)
                            if (testResult.isNotBlank()) {
                                Text("Status: $testResult", color = if (testResult == "Working") Color.Green else Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    isTesting = true
                                    testResult = "Testing..."
                                    scope.launch {
                                        testResult = JioTvRepo.testStreamUrl(entry.url, entry.headers)
                                        isTesting = false
                                    }
                                }, modifier = Modifier.weight(1f), enabled = !isTesting) {
                                    Text(if (isTesting) "..." else "TEST")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

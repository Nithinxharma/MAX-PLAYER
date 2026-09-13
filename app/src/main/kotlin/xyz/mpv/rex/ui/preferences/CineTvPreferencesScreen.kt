package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xyz.mpv.rex.cinetv.data.JioTvRepo
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object CineTvPreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val backstack = LocalBackStack.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var mobileNumber by remember { mutableStateOf("") }
        var otp by remember { mutableStateOf("") }
        var isOtpSent by remember { mutableStateOf(false) }
        var isUserAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }
        var isSaving by remember { mutableStateOf(false) }
        var saveMessage by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CineTV Settings") },
                    navigationIcon = {
                        IconButton(onClick = { backstack.removeLast() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("JioTV Login", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isUserAuthed) {
                                Text(
                                    "You are logged in to JioTV.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        JioTvRepo.logout(context)
                                        isUserAuthed = false
                                        saveMessage = "Logged out successfully."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Log Out")
                                }
                            } else {
                                Text(
                                    "Log in to JioTV using your mobile number and OTP to enable live TV streaming.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = mobileNumber,
                                    onValueChange = { mobileNumber = it },
                                    label = { Text("Mobile Number (without +91)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    enabled = !isOtpSent
                                )

                                if (isOtpSent) {
                                    OutlinedTextField(
                                        value = otp,
                                        onValueChange = { otp = it },
                                        label = { Text("Enter OTP") },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSaving = true
                                            if (!isOtpSent) {
                                                val success = JioTvRepo.requestOtp(mobileNumber)
                                                if (success) {
                                                    isOtpSent = true
                                                    saveMessage = "OTP sent to your mobile."
                                                } else {
                                                    saveMessage = "Failed to send OTP."
                                                }
                                            } else {
                                                val success = JioTvRepo.verifyOtp(context, mobileNumber, otp)
                                                if (success) {
                                                    isUserAuthed = true
                                                    saveMessage = "Logged in successfully!"
                                                } else {
                                                    saveMessage = "Invalid OTP or failed to verify."
                                                }
                                            }
                                            isSaving = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isSaving
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isOtpSent) "Verify OTP" else "Request OTP")
                                    }
                                }
                            }

                            saveMessage?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

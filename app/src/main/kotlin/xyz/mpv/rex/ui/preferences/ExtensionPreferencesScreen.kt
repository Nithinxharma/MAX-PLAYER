package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.zhanghai.compose.preference.Preference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPreferencesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    onNavigateToInstalled: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Extensions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(text = "Extension Management", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

            Preference(
                title = { Text("Installed Extensions") },
                summary = { Text("Manage your installed provider plugins") },
                onClick = onNavigateToInstalled
            )

            Preference(
                title = { Text("Extension Repositories") },
                summary = { Text("Add or remove repository sources") },
                onClick = onNavigateToRepositories
            )
        }
    }
}

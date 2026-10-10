package xyz.mpv.rex.ui.preferences

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassButton
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
data class ExtensionDetailScreen(
  val extensionName: String = "English Providers",
  val version: String = "3.2.0",
  val author: String = "CloudStream Devs",
  val repositoryUrl: String = "https://raw.githubusercontent.com/recloudstream/extensions",
) : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current

    BackHandler {
      backstack.removeLastOrNull()
    }

    var autoUpdate by remember { mutableStateOf(true) }
    var flixHqEnabled by remember { mutableStateOf(true) }
    var superStreamEnabled by remember { mutableStateOf(true) }
    var vidSrcEnabled by remember { mutableStateOf(true) }

    Scaffold(
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaxStreamTheme.TextPrimary
              )
            }
          },
          title = {
            Text(
              text = extensionName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaxStreamTheme.TextPrimary
            )
          }
        )
      },
      containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Manifest Overview
        item {
          GlassCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaxStreamTheme.CrimsonAccent
                  )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                  Text(
                    text = extensionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaxStreamTheme.TextPrimary
                  )
                  Text(
                    text = "v$version • Author: $author",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxStreamTheme.TextSecondary
                  )
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Auto-update extension",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaxStreamTheme.TextPrimary
                )
                Switch(
                  checked = autoUpdate,
                  onCheckedChange = { autoUpdate = it },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaxStreamTheme.CrimsonAccent
                  )
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                GlassButton(
                  text = "Clear Cache",
                  onClick = { /* Clear provider cache */ },
                  modifier = Modifier.weight(1f)
                )
                GlassButton(
                  text = "Check Updates",
                  onClick = { /* Trigger update check */ },
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }

        item {
          Text(
            text = "Bundled Media Providers",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaxStreamTheme.TextPrimary,
            modifier = Modifier.padding(top = 8.dp)
          )
        }

        item {
          GlassCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = "FlixHQ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaxStreamTheme.TextPrimary)
                Text(text = "Movies & TV Shows • Multi-Server", style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.TextSecondary)
              }
              Switch(
                checked = flixHqEnabled,
                onCheckedChange = { flixHqEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaxStreamTheme.CrimsonAccent)
              )
            }
          }
        }

        item {
          GlassCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = "SuperStream", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaxStreamTheme.TextPrimary)
                Text(text = "4K Movies & Series", style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.TextSecondary)
              }
              Switch(
                checked = superStreamEnabled,
                onCheckedChange = { superStreamEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaxStreamTheme.CrimsonAccent)
              )
            }
          }
        }

        item {
          GlassCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = "VidSrc", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaxStreamTheme.TextPrimary)
                Text(text = "Instant Extractor & Subtitles", style = MaterialTheme.typography.labelSmall, color = MaxStreamTheme.TextSecondary)
              }
              Switch(
                checked = vidSrcEnabled,
                onCheckedChange = { vidSrcEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaxStreamTheme.CrimsonAccent)
              )
            }
          }
        }
      }
    }
  }
}

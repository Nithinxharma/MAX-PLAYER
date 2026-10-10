package xyz.mpv.rex.ui.sync

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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
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

data class SyncAccountState(
  val name: String,
  val serviceKey: String,
  val isConnected: Boolean,
  val username: String? = null,
  val avatarUrl: String? = null,
  val lastSynced: String? = null,
  val accentColor: Color,
)

@Serializable
object CloudSyncScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current

    BackHandler {
      backstack.removeLastOrNull()
    }

    var autoScrobbleEnabled by remember { mutableStateOf(true) }
    var syncWatchlistEnabled by remember { mutableStateOf(true) }

    val accounts = remember {
      listOf(
        SyncAccountState(
          name = "Trakt.tv",
          serviceKey = "trakt",
          isConnected = true,
          username = "CineStreamUser",
          lastSynced = "10 mins ago",
          accentColor = Color(0xFFED1C24)
        ),
        SyncAccountState(
          name = "AniList",
          serviceKey = "anilist",
          isConnected = true,
          username = "OtakuMax",
          lastSynced = "1 hour ago",
          accentColor = Color(0xFF02A9FF)
        ),
        SyncAccountState(
          name = "MyAnimeList",
          serviceKey = "mal",
          isConnected = false,
          accentColor = Color(0xFF2E51A2)
        ),
        SyncAccountState(
          name = "Simkl",
          serviceKey = "simkl",
          isConnected = false,
          accentColor = Color(0xFF000000)
        )
      )
    }

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
              text = "Cloud Sync & Accounts",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaxStreamTheme.TextPrimary
            )
          },
          actions = {
            IconButton(onClick = { /* Force full sync */ }) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync Now",
                tint = MaxStreamTheme.CrimsonAccent
              )
            }
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
        // Hero Sync Card
        item {
          GlassCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaxStreamTheme.CrimsonAccent
                  )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                  Text(
                    text = "Library & History Sync",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaxStreamTheme.TextPrimary
                  )
                  Text(
                    text = "Automatically sync watch progress with your cloud accounts",
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
                  text = "Auto-Scrobble at 85% playback",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaxStreamTheme.TextPrimary
                )
                Switch(
                  checked = autoScrobbleEnabled,
                  onCheckedChange = { autoScrobbleEnabled = it },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaxStreamTheme.CrimsonAccent
                  )
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Sync Watchlists & Bookmarks",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaxStreamTheme.TextPrimary
                )
                Switch(
                  checked = syncWatchlistEnabled,
                  onCheckedChange = { syncWatchlistEnabled = it },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaxStreamTheme.CrimsonAccent
                  )
                )
              }
            }
          }
        }

        item {
          Text(
            text = "Connected Sync Accounts",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaxStreamTheme.TextPrimary,
            modifier = Modifier.padding(top = 8.dp)
          )
        }

        items(accounts.size) { index ->
          val account = accounts[index]
          GlassCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(account.accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.AccountCircle,
                  contentDescription = null,
                  tint = account.accentColor
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = account.name,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaxStreamTheme.TextPrimary
                )
                if (account.isConnected) {
                  Text(
                    text = "@${account.username} • Synced ${account.lastSynced}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaxStreamTheme.TextSecondary
                  )
                } else {
                  Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaxStreamTheme.TextSecondary
                  )
                }
              }

              if (account.isConnected) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Connected",
                  tint = Color(0xFF4CAF50),
                  modifier = Modifier.size(24.dp)
                )
              } else {
                GlassButton(
                  text = "Connect",
                  onClick = { /* Launch OAuth flow */ }
                )
              }
            }
          }
        }
      }
    }
  }
}

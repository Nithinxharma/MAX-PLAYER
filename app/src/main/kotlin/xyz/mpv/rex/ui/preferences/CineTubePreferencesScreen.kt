package xyz.mpv.rex.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.youtube.data.InvidiousClient
import xyz.mpv.rex.youtube.invidious.cache.InvidiousCacheService
import xyz.mpv.rex.youtube.invidious.model.InvidiousInstance

@Serializable
object CineTubePreferencesScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failoverClient = InvidiousClient.failoverClient
    val cacheService = remember { InvidiousCacheService(context) }

    var activeClient by remember { mutableStateOf(failoverClient.getActiveBaseUrl()) }
    var instances by remember { mutableStateOf<List<InvidiousInstance>>(emptyList()) }
    var isLoadingInstances by remember { mutableStateOf(true) }
    var isPingingBest by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var customUrlInput by remember { mutableStateOf("") }
    var isTestingCustom by remember { mutableStateOf(false) }

    val instancePings = remember { mutableStateMapOf<String, Long>() }

    fun loadInstances(forceRefresh: Boolean = false) {
      scope.launch(Dispatchers.IO) {
        isLoadingInstances = true
        val list = cacheService.getRankedInstances(forceRefresh)
        withContext(Dispatchers.Main) {
          instances = list
          isLoadingInstances = false
        }
      }
    }

    LaunchedEffect(Unit) {
      loadInstances(false)
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("CineTube (Invidious)") },
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
          },
          actions = {
            IconButton(
              onClick = { loadInstances(true) },
              enabled = !isLoadingInstances
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh Instances")
            }
          }
        )
      }
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Active Client Card
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
              ) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Active Invidious Client",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = activeClient,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              Spacer(modifier = Modifier.height(12.dp))

              // Auto-Select Best Client Button
              Button(
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    isPingingBest = true
                    withContext(Dispatchers.Main) {
                      pingStatus = "Testing response times across top instances..."
                    }

                    val candidatesToTest = instances.take(8).ifEmpty {
                      InvidiousCacheService.SEED_INSTANCES
                    }

                    val deferredList = candidatesToTest.map { inst ->
                      async {
                        val latency = failoverClient.pingInstance(inst.baseUrl)
                        Pair(inst, latency)
                      }
                    }

                    val results = deferredList.awaitAll()
                    val responsive = results.filter { it.second != null }.sortedBy { it.second ?: Long.MAX_VALUE }

                    withContext(Dispatchers.Main) {
                      results.forEach { (inst, ping) ->
                        if (ping != null) {
                          instancePings[inst.baseUrl] = ping
                        }
                      }
                      isPingingBest = false

                      if (responsive.isNotEmpty()) {
                        val (bestInstance, bestLatency) = responsive.first()
                        failoverClient.setActiveBaseUrl(bestInstance.baseUrl)
                        activeClient = bestInstance.baseUrl
                        pingStatus = "Switched to fastest client: ${bestInstance.domain} (${bestLatency}ms)"
                        Toast.makeText(context, "Connected to ${bestInstance.domain} (${bestLatency}ms)", Toast.LENGTH_SHORT).show()
                      } else {
                        pingStatus = "Could not reach online instances; kept current client."
                        Toast.makeText(context, "Failed to connect to instances", Toast.LENGTH_SHORT).show()
                      }
                    }
                  }
                },
                enabled = !isPingingBest && !isLoadingInstances,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              ) {
                if (isPingingBest) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Selecting Best Client...")
                } else {
                  Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Auto-Select Best Client", fontWeight = FontWeight.Bold)
                }
              }

              if (pingStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = pingStatus ?: "",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
            }
          }
        }

        // Custom Client Section
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Custom Invidious Instance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Enter a self-hosted or preferred Invidious instance URL",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedTextField(
                value = customUrlInput,
                onValueChange = { customUrlInput = it },
                placeholder = { Text("https://invidious.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              )
              Spacer(modifier = Modifier.height(10.dp))
              Button(
                onClick = {
                  val trimmed = customUrlInput.trim().trimEnd('/')
                  if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    Toast.makeText(context, "URL must start with https:// or http://", Toast.LENGTH_SHORT).show()
                    return@Button
                  }
                  scope.launch(Dispatchers.IO) {
                    isTestingCustom = true
                    val latency = failoverClient.pingInstance(trimmed)
                    withContext(Dispatchers.Main) {
                      isTestingCustom = false
                      if (latency != null) {
                        failoverClient.setActiveBaseUrl(trimmed)
                        activeClient = trimmed
                        customUrlInput = ""
                        Toast.makeText(context, "Custom client saved! (${latency}ms)", Toast.LENGTH_SHORT).show()
                      } else {
                        Toast.makeText(context, "Instance unreachable or invalid Invidious API", Toast.LENGTH_LONG).show()
                      }
                    }
                  }
                },
                enabled = customUrlInput.isNotBlank() && !isTestingCustom,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              ) {
                if (isTestingCustom) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Testing Connection...")
                } else {
                  Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Test & Set Custom Client")
                }
              }
            }
          }
        }

        // Switch Client Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Available Invidious Clients",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            if (isLoadingInstances) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
              Text(
                text = "${instances.size} healthy instances",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Instance List
        items(instances) { inst ->
          val isSelected = inst.baseUrl.trimEnd('/').equals(activeClient.trimEnd('/'), ignoreCase = true)
          val ping = instancePings[inst.baseUrl]

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                failoverClient.setActiveBaseUrl(inst.baseUrl)
                activeClient = inst.baseUrl
                Toast.makeText(context, "Switched to ${inst.domain}", Toast.LENGTH_SHORT).show()
              },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
              }
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = isSelected,
                onClick = {
                  failoverClient.setActiveBaseUrl(inst.baseUrl)
                  activeClient = inst.baseUrl
                  Toast.makeText(context, "Switched to ${inst.domain}", Toast.LENGTH_SHORT).show()
                }
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = inst.domain,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "${String.format("%.1f", inst.healthRatio)}% Health",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold
                  )
                  if (ping != null) {
                    Text(
                      text = " • ${ping}ms",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Text(
                    text = " • ${inst.type.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              OutlinedButton(
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val latency = failoverClient.pingInstance(inst.baseUrl)
                    withContext(Dispatchers.Main) {
                      if (latency != null) {
                        instancePings[inst.baseUrl] = latency
                        Toast.makeText(context, "${inst.domain}: ${latency}ms", Toast.LENGTH_SHORT).show()
                      } else {
                        Toast.makeText(context, "${inst.domain} failed to respond", Toast.LENGTH_SHORT).show()
                      }
                    }
                  }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(text = "Ping", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}

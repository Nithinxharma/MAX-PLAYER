package xyz.mpv.rex.cinetv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinetv.data.JioTvRepo
import xyz.mpv.rex.cinetv.model.*
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import java.text.SimpleDateFormat
import java.util.*

val globalPaidChannels = mutableStateMapOf<String, Boolean>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvTabScreen(
    searchQuery: String, 
    onPlayRequested: (streamUrl: String, channelTitle: String, metadata: Map<String, String>) -> Unit 
) {
    val context = LocalContext.current
    val backstack = xyz.mpv.rex.ui.utils.LocalBackStack.current
    val scope = rememberCoroutineScope()

    var activeSubTab by remember { mutableStateOf(LiveTab.CHANNELS) }
    var userAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }

    var allChannels by remember { mutableStateOf(emptyList<LiveChannelItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var fetchError by remember { mutableStateOf<String?>(null) } 

    var isSearchVisible by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }
    var languageExpanded by remember { mutableStateOf(false) }

    var smartCache by remember { mutableStateOf(mapOf<String, ChannelCacheEntry>()) }
    var m3uEntries by remember { mutableStateOf(emptyList<JioTvRepo.M3uEntry>()) }

    var pendingFeedbackData by remember { mutableStateOf<Pair<LiveChannelItem, String>?>(null) }
    
    // Bottom Sheet State for linking
    var m3uToLink by remember { mutableStateOf<JioTvRepo.M3uEntry?>(null) }
    var linkSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        JioTvRepo.initTokens(context)
        userAuthed = JioTvRepo.isUserLoggedIn()
        smartCache = JioTvRepo.getChannelCacheMap(context)
        m3uEntries = JioTvRepo.loadM3uFallback(context)

        isLoading = true
        try {
            fetchError = null
            allChannels = JioTvRepo.fetchLiveChannelsFromAssets(context)
        } catch (e: Exception) {
            fetchError = e.message ?: "An unknown error occurred while downloading channels"
        } finally {
            isLoading = false
        }
    }

    val availableGenres = remember(allChannels) { listOf("All") + allChannels.map { it.category }.distinct().sorted() }
    val availableLanguages = remember(allChannels) { listOf("All Languages") + allChannels.flatMap { it.variants.map { v -> v.language } }.distinct().sorted() }

    val filteredChannels = remember(allChannels, selectedGenre, selectedLanguage, localSearchQuery, searchQuery) {
        allChannels.filter { channel ->
            val matchesGenre = selectedGenre == "All" || channel.category == selectedGenre
            val matchesLanguage = selectedLanguage == "All Languages" || channel.variants.any { it.language.equals(selectedLanguage, true) }

            val activeSearch = if (isSearchVisible && localSearchQuery.isNotBlank()) localSearchQuery else searchQuery
            val searchLower = activeSearch.trim().lowercase()

            val cacheEntry = smartCache[channel.defaultChannelId]
            val aliasName = cacheEntry?.mappedM3uName?.lowercase() ?: ""
            // Use lastSuccessfulUrl for manualName matching safely without requiring model changes
            val manualName = cacheEntry?.lastSuccessfulUrl?.lowercase() ?: ""

            val matchesSearch = searchLower.isBlank() || 
                channel.title.lowercase().contains(searchLower) || 
                channel.category.lowercase().contains(searchLower) ||
                aliasName.contains(searchLower) ||
                manualName.contains(searchLower) ||
                channel.variants.any { it.language.lowercase().contains(searchLower) }

            matchesGenre && matchesLanguage && matchesSearch
        }
    }

    val playChannel: (LiveChannelItem, String) -> Unit = { channel, idToPlay ->
        scope.launch {
            try {
                val resolved = JioTvRepo.getResolvedLiveUrl(context, idToPlay, channel.title)
                JioTvRepo.lastResolvedHeaders = resolved.headers
                
                val cacheEntry = smartCache[idToPlay]
                if (cacheEntry?.userVerified != true && cacheEntry?.isManualMapping != true) {
                    pendingFeedbackData = channel to resolved.url
                } else {
                    pendingFeedbackData = null
                }
                
                onPlayRequested(resolved.url, channel.title, mapOf("Logo" to (channel.logoUrl ?: ""), "Genre" to channel.category, "SourceType" to "cinetv"))
            } catch (e: Exception) {
                Toast.makeText(context, "No working streams found for ${channel.title}", Toast.LENGTH_SHORT).show()
                pendingFeedbackData = null
            }
        }
    }

    if (pendingFeedbackData != null) {
        val (channelToFeed, playedUrl) = pendingFeedbackData!!
        AlertDialog(
            onDismissRequest = { /* Must force interaction */ },
            title = { Text("Playback Feedback") },
            text = { Text("Did ${channelToFeed.title} play correctly?\n\n(Wait for stream to load. Video must render and audio must start.)") },
            confirmButton = {
                Button(onClick = {
                    JioTvRepo.handleUserPlaybackFeedback(context, channelToFeed.defaultChannelId, true, playedUrl, channelToFeed.title)
                    smartCache = JioTvRepo.getChannelCacheMap(context)
                    pendingFeedbackData = null
                }) { Text("Yes") }
            },
            dismissButton = {
                Button(onClick = {
                    JioTvRepo.handleUserPlaybackFeedback(context, channelToFeed.defaultChannelId, false, playedUrl, channelToFeed.title)
                    smartCache = JioTvRepo.getChannelCacheMap(context)
                    pendingFeedbackData = null
                    
                    Toast.makeText(context, "Searching alternative stream...", Toast.LENGTH_SHORT).show()
                    playChannel(channelToFeed, channelToFeed.defaultChannelId) 
                }) { Text("No") }
            }
        )
    }

    // Smart Filter Bottom Sheet for Linking M3U to Jio Channel
    if (m3uToLink != null) {
        ModalBottomSheet(onDismissRequest = { m3uToLink = null; linkSearchQuery = "" }) {
            Column(Modifier.padding(16.dp).fillMaxSize()) {
                Text("Associate With Jio Channel", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(m3uToLink!!.name, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = linkSearchQuery,
                    onValueChange = { linkSearchQuery = it },
                    label = { Text("Search Jio Channels") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                
                val smartKeyword = remember(m3uToLink) { JioTvRepo.generateSmartFilterKeyword(m3uToLink!!.name) }
                val currentLinkSearch = linkSearchQuery.ifBlank { smartKeyword }
                
                val filteredJioForLink = allChannels.filter { 
                    it.title.lowercase().contains(currentLinkSearch.lowercase()) ||
                    it.category.lowercase().contains(currentLinkSearch.lowercase())
                }.take(20)

                if (filteredJioForLink.isEmpty()) {
                    Text("No matches found. Try clearing the search box.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        item {
                            if (linkSearchQuery.isBlank()) {
                                Text("Smart Suggestions for '$smartKeyword'", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        items(filteredJioForLink) { jioCh ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    JioTvRepo.saveManualMapping(context, jioCh.defaultChannelId, m3uToLink!!.name, m3uToLink!!.url)
                                    smartCache = JioTvRepo.getChannelCacheMap(context)
                                    Toast.makeText(context, "Mapping saved permanently!", Toast.LENGTH_SHORT).show()
                                    m3uToLink = null
                                    linkSearchQuery = ""
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(model = jioCh.logoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(jioCh.title, fontWeight = FontWeight.Bold)
                                    Text("${jioCh.category} • ${jioCh.defaultLanguage}", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { linkSearchQuery = " " }, modifier = Modifier.fillMaxWidth()) {
                                Text("Show All Channels")
                            }
                        }
                    }
                }
            }
        }
    }

    // Main Scaffold with exact specified structure
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BrowserTopBar(
                        title = "CineTV",
                        isInSelectionMode = false,
                        selectedCount = 0,
                        totalCount = allChannels.size,
                        onCancelSelection = {},
                        isHomeScreen = true
                    )
                    
                    // Independent actions overlaid horizontally on the right side
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSearchVisible
                ) {
                    OutlinedTextField(
                        value = localSearchQuery,
                        onValueChange = { localSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search channels, aliases...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            when (activeSubTab) {
                LiveTab.CHANNELS -> {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = availableGenres) { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = { selectedGenre = genre },
                                label = { Text(genre, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                ),
                                shape = CircleShape
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = languageExpanded, 
                            onExpandedChange = { languageExpanded = it }, 
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedLanguage, onValueChange = {}, readOnly = true,
                                label = { Text("Select Language", fontSize = 12.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                                availableLanguages.forEach { lang ->
                                    DropdownMenuItem(text = { Text(lang, fontSize = 12.sp) }, onClick = { selectedLanguage = lang; languageExpanded = false })
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (fetchError != null) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(fetchError!!, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1), modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = filteredChannels) { channel ->
                                val channelLangId = channel.getIdForLanguage(if (selectedLanguage != "All Languages") selectedLanguage else channel.defaultLanguage)
                                val entry = smartCache[channelLangId]

                                LiveChannelRowItem(
                                    channel = channel,
                                    currentActiveId = channelLangId,
                                    isM3uFallback = entry?.preferredSource == PlaybackSource.M3U || entry?.isManualMapping == true,
                                    confidenceScore = entry?.confidenceScore ?: 0,
                                    isManualMapping = entry?.isManualMapping ?: false,
                                    onPlayRequested = { id -> playChannel(channel, id) }
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun LiveChannelRowItem(
    channel: LiveChannelItem, 
    currentActiveId: String,
    isM3uFallback: Boolean,
    confidenceScore: Int,
    isManualMapping: Boolean,
    onPlayRequested: (channelId: String) -> Unit
) {
    val isPaid = globalPaidChannels[currentActiveId] == true

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPlayRequested(currentActiveId) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = channel.title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.category} • ${channel.variants.find { it.channelId == currentActiveId }?.language ?: channel.defaultLanguage}", 
                         fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    if (isPaid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" 🟡 Paid ", fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                        }
                    }
                    if (isM3uFallback) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" ✓ Synced ", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

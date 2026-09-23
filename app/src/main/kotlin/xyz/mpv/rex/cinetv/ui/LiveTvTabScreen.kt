package xyz.mpv.rex.cinetv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinetv.data.JioTvRepo
import xyz.mpv.rex.cinetv.model.*
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamLiquidGlassSearch
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
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
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    // Theme Adaptive Color Tokens
    val bgColor = if (isDark) MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val glassSurface = if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val elevatedSurface = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val glassBorder = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    var activeSubTab by remember { mutableStateOf(LiveTab.CHANNELS) }
    var userAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }

    var allChannels by remember { mutableStateOf(emptyList<LiveChannelItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }
    var languageExpanded by remember { mutableStateOf(false) }
    var seeAllCategory by remember { mutableStateOf<String?>(null) }

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

    val availableGenres = remember(allChannels) {
        listOf("All") + allChannels.map { it.category }.distinct().sorted()
    }
    val availableLanguages = remember(allChannels) {
        listOf("All Languages") + allChannels.flatMap { it.variants.map { v -> v.language } }.distinct().sorted()
    }

    val activeSearch = if (isSearchExpanded && localSearchQuery.isNotBlank()) localSearchQuery else searchQuery

    val filteredChannels = remember(allChannels, selectedGenre, selectedLanguage, activeSearch) {
        allChannels.filter { channel ->
            val matchesGenre = selectedGenre == "All" || channel.category.equals(selectedGenre, ignoreCase = true)
            val matchesLanguage = selectedLanguage == "All Languages" || channel.variants.any { it.language.equals(selectedLanguage, true) }

            val searchLower = activeSearch.trim().lowercase()

            val cacheEntry = smartCache[channel.defaultChannelId]
            val aliasName = cacheEntry?.mappedM3uName?.lowercase() ?: ""
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

    // Featured hero channels (e.g. Sports or popular entertainment)
    val featuredChannels = remember(allChannels) {
        allChannels.filter {
            it.category.equals("Sports", ignoreCase = true) ||
            it.category.equals("Entertainment", ignoreCase = true) ||
            it.category.equals("Movies", ignoreCase = true)
        }.take(5)
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

                onPlayRequested(
                    resolved.url,
                    channel.title,
                    mapOf(
                        "Logo" to (channel.logoUrl ?: ""),
                        "Genre" to channel.category,
                        "SourceType" to "cinetv"
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(context, "No working streams found for ${channel.title}", Toast.LENGTH_SHORT).show()
                pendingFeedbackData = null
            }
        }
    }

    // Playback Feedback Dialog
    if (pendingFeedbackData != null) {
        val (channelToFeed, playedUrl) = pendingFeedbackData!!
        AlertDialog(
            onDismissRequest = { /* Force interaction */ },
            title = {
                Text(
                    "Playback Feedback",
                    color = primaryTextColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Did ${channelToFeed.title} play correctly?\n\n(Wait for stream to load. Video must render and audio must start.)",
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        JioTvRepo.handleUserPlaybackFeedback(context, channelToFeed.defaultChannelId, true, playedUrl, channelToFeed.title)
                        smartCache = JioTvRepo.getChannelCacheMap(context)
                        pendingFeedbackData = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                ) {
                    Text("Yes, Working", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        JioTvRepo.handleUserPlaybackFeedback(context, channelToFeed.defaultChannelId, false, playedUrl, channelToFeed.title)
                        smartCache = JioTvRepo.getChannelCacheMap(context)
                        pendingFeedbackData = null

                        Toast.makeText(context, "Searching alternative stream...", Toast.LENGTH_SHORT).show()
                        playChannel(channelToFeed, channelToFeed.defaultChannelId)
                    }
                ) {
                    Text("No, Try Alternate", color = primaryTextColor)
                }
            },
            containerColor = if (isDark) MaxStreamTheme.MidnightSurface else MaterialTheme.colorScheme.surface
        )
    }

    // M3U Linking Bottom Sheet
    if (m3uToLink != null) {
        ModalBottomSheet(
            onDismissRequest = { m3uToLink = null; linkSearchQuery = "" },
            containerColor = if (isDark) MaxStreamTheme.MidnightSurface else MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = mutedTextColor) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "Associate With Jio Channel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = primaryTextColor
                )
                Text(
                    m3uToLink!!.name,
                    color = MaxStreamTheme.CrimsonAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = linkSearchQuery,
                    onValueChange = { linkSearchQuery = it },
                    placeholder = { Text("Search Jio Channels...", color = mutedTextColor) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor,
                        focusedBorderColor = MaxStreamTheme.CrimsonAccent,
                        unfocusedBorderColor = glassBorder
                    ),
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
                    Text(
                        "No matches found. Try typing a channel title above.",
                        color = mutedTextColor,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            if (linkSearchQuery.isBlank()) {
                                Text(
                                    "Smart Suggestions for '$smartKeyword'",
                                    color = MaxStreamTheme.ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        items(filteredJioForLink) { jioCh ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = elevatedSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        JioTvRepo.saveManualMapping(context, jioCh.defaultChannelId, m3uToLink!!.name, m3uToLink!!.url)
                                        smartCache = JioTvRepo.getChannelCacheMap(context)
                                        Toast.makeText(context, "Mapping saved permanently!", Toast.LENGTH_SHORT).show()
                                        m3uToLink = null
                                        linkSearchQuery = ""
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = jioCh.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDark) Color.Black.copy(alpha = 0.3f) else Color.White)
                                            .padding(4.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(jioCh.title, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                        Text("${jioCh.category} • ${jioCh.defaultLanguage}", color = secondaryTextColor, fontSize = 12.sp)
                                    }
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = "Link",
                                        tint = MaxStreamTheme.CrimsonAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // See All Category Sheet
    if (seeAllCategory != null) {
        val catName = seeAllCategory!!
        val catChannels = remember(catName, allChannels) {
            allChannels.filter { it.category.equals(catName, ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { seeAllCategory = null },
            containerColor = if (isDark) MaxStreamTheme.MidnightSurface else MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = mutedTextColor) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = catName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = primaryTextColor
                        )
                        Text(
                            text = "${catChannels.size} live channels available",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                    IconButton(onClick = { seeAllCategory = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = primaryTextColor)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(catChannels) { channel ->
                        val channelLangId = channel.getIdForLanguage(if (selectedLanguage != "All Languages") selectedLanguage else channel.defaultLanguage)
                        val entry = smartCache[channelLangId]
                        LiveTvGridChannelCard(
                            channel = channel,
                            currentActiveId = channelLangId,
                            isM3uFallback = entry?.preferredSource == PlaybackSource.M3U || entry?.isManualMapping == true,
                            onPlayRequested = { id ->
                                seeAllCategory = null
                                playChannel(channel, id)
                            }
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BrowserTopBar(
                title = "CineTV",
                isInSelectionMode = false,
                selectedCount = 0,
                totalCount = allChannels.size,
                onCancelSelection = {},
                isHomeScreen = true,
                onSearchClick = {
                    isSearchExpanded = !isSearchExpanded
                },
                onSettingsClick = {
                    activeSubTab = if (activeSubTab == LiveTab.CHANNELS) LiveTab.JIO_LOGIN else LiveTab.CHANNELS
                }
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeSubTab) {
                LiveTab.CHANNELS -> {
                    // Liquid Glass Search Bar & Category Switcher Row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        MaxStreamLiquidGlassSearch(
                            query = localSearchQuery,
                            onQueryChange = {
                                localSearchQuery = it
                                if (it.isNotEmpty()) isSearchExpanded = true
                            },
                            isExpanded = isSearchExpanded,
                            onExpandedChange = { isSearchExpanded = it },
                            placeholder = "Search live channels, sports, news…",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category Chips Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableGenres) { genre ->
                                val isSelected = selectedGenre == genre
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaxStreamTheme.CrimsonAccent else elevatedSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaxStreamTheme.CrimsonAccent else glassBorder
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectedGenre = genre }
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isSelected) Color.White else primaryTextColor,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Content Area
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaxStreamTheme.CrimsonAccent)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading CineTV Live Channels…", color = secondaryTextColor, fontSize = 13.sp)
                            }
                        }
                    } else if (fetchError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(fetchError!!, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            // Featured Live Hero Banner (Show when search is empty and "All" genre is selected)
                            if (activeSearch.isBlank() && selectedGenre == "All" && featuredChannels.isNotEmpty()) {
                                item {
                                    CineTvHeroBanner(
                                        featuredChannels = featuredChannels,
                                        onPlayChannel = { ch -> playChannel(ch, ch.defaultChannelId) }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // Language & Filter Sub-Bar
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (activeSearch.isNotBlank()) "Search Results (${filteredChannels.size})"
                                               else if (selectedGenre != "All") "$selectedGenre Channels (${filteredChannels.size})"
                                               else "All Live Channels (${filteredChannels.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = primaryTextColor
                                    )

                                    // Language Selector Menu
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = elevatedSurface,
                                            border = BorderStroke(1.dp, glassBorder),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { languageExpanded = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Translate,
                                                    contentDescription = null,
                                                    tint = MaxStreamTheme.CrimsonAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = selectedLanguage,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = primaryTextColor
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = secondaryTextColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = languageExpanded,
                                            onDismissRequest = { languageExpanded = false }
                                        ) {
                                            availableLanguages.forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang, fontSize = 12.sp) },
                                                    onClick = {
                                                        selectedLanguage = lang
                                                        languageExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // If "All" is selected and no active search: Render categorized rails (Sports, News, Entertainment, Movies, etc.)
                            if (activeSearch.isBlank() && selectedGenre == "All") {
                                val railCategories = listOf("Sports", "News", "Entertainment", "Movies", "Music", "Kids", "Infotainment")
                                railCategories.forEach { category ->
                                    val catItems = allChannels.filter { it.category.equals(category, ignoreCase = true) }
                                    if (catItems.isNotEmpty()) {
                                        item {
                                            CineTvCategoryRail(
                                                category = category,
                                                channels = catItems,
                                                smartCache = smartCache,
                                                selectedLanguage = selectedLanguage,
                                                onSeeAllClick = { seeAllCategory = category },
                                                onPlayChannel = { ch, id -> playChannel(ch, id) }
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
                            } else {
                                // Filtered or Search View: Show channel items
                                items(filteredChannels) { channel ->
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                LiveTab.JIO_LOGIN -> {
                    BackHandler {
                        activeSubTab = LiveTab.CHANNELS
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        item {
                            // Header banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { activeSubTab = LiveTab.CHANNELS }) {
                                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = primaryTextColor)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "CineTV Settings & Playlist",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = primaryTextColor
                                    )
                                }
                            }
                        }

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
                                    if (userAuthed) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaxStreamTheme.ElectricCyan,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "Authenticated Session Active",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = primaryTextColor
                                        )
                                        Text(
                                            "High-speed direct HLS playback unlocked",
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = { JioTvRepo.logout(context); userAuthed = false },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Revoke Session", color = Color.White)
                                        }
                                    } else {
                                        var mobile by remember { mutableStateOf("") }
                                        var otpCode by remember { mutableStateOf("") }
                                        var isOtpSent by remember { mutableStateOf(false) }

                                        Text(
                                            "JioTV Login (Optional)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = primaryTextColor
                                        )
                                        Text(
                                            "Login with OTP or use direct M3U sync below",
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))

                                        OutlinedTextField(
                                            value = mobile,
                                            onValueChange = { mobile = it },
                                            label = { Text("Mobile Number") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        if (isOtpSent) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = otpCode,
                                                onValueChange = { otpCode = it },
                                                label = { Text("Enter OTP Code") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent),
                                            onClick = {
                                                scope.launch {
                                                    if (!isOtpSent) {
                                                        try {
                                                            val sent = JioTvRepo.requestOtp(mobile)
                                                            isOtpSent = sent
                                                            if (sent) Toast.makeText(context, "OTP Sent", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_LONG).show()
                                                        }
                                                    } else {
                                                        try {
                                                            val success = JioTvRepo.verifyOtp(context, mobile, otpCode)
                                                            if (success) userAuthed = true
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, e.message ?: "Verification Failed", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(if (!isOtpSent) "Send OTP" else "Verify Token", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(20.dp)) }

                        // Sync Playlist (Direct URL)
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = elevatedSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Sync Playlist (Direct URL)", fontWeight = FontWeight.Bold, color = MaxStreamTheme.CrimsonAccent)
                                    Spacer(Modifier.height(8.dp))
                                    var m3uUrl by remember { mutableStateOf("") }
                                    OutlinedTextField(
                                        value = m3uUrl,
                                        onValueChange = { m3uUrl = it },
                                        placeholder = { Text("https://abc.xyz/live.m3u", color = mutedTextColor) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                                val success = JioTvRepo.syncPlaylistFromUrl(context, m3uUrl)
                                                if (success) {
                                                    m3uEntries = JioTvRepo.loadM3uFallback(context)
                                                    Toast.makeText(context, "✓ Synced Successfully", Toast.LENGTH_LONG).show()
                                                } else Toast.makeText(context, "Failed to sync", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                                    ) { Text("SYNC PLAYLIST", color = Color.White) }

                                    val meta = remember(m3uEntries) { JioTvRepo.getPlaylistMeta(context) }
                                    if (meta.channelCount > 0) {
                                        Spacer(Modifier.height(12.dp))
                                        Text("Playlist: ${meta.name}", fontSize = 12.sp, color = primaryTextColor)
                                        Text("Channels: ${meta.channelCount}", fontSize = 12.sp, color = secondaryTextColor)
                                        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(meta.lastUpdated))
                                        Text("Last Updated: $dateStr", fontSize = 12.sp, color = mutedTextColor)
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }

                        // Local File Mode / Clipboard
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = elevatedSurface,
                                border = BorderStroke(1.dp, glassBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Local M3U Clipboard Tools", fontWeight = FontWeight.Bold, color = MaxStreamTheme.ElectricCyan)
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val text = cb.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!text.isNullOrBlank() && text.contains("#EXTM3U")) {
                                                JioTvRepo.saveM3uText(context, text)
                                                m3uEntries = JioTvRepo.loadM3uFallback(context)
                                                Toast.makeText(context, "Imported from clipboard!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No valid M3U in clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text("Import M3U", color = primaryTextColor) }

                                        OutlinedButton(onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("M3U", JioTvRepo.readM3uText(context)))
                                            Toast.makeText(context, "Exported to clipboard!", Toast.LENGTH_SHORT).show()
                                        }) { Text("Export M3U", color = primaryTextColor) }

                                        OutlinedButton(onClick = {
                                            JioTvRepo.reloadM3uParser()
                                            m3uEntries = JioTvRepo.loadM3uFallback(context)
                                            Toast.makeText(context, "Parser Reloaded", Toast.LENGTH_SHORT).show()
                                        }) { Text("Reload Parser", color = primaryTextColor) }
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
                                                            TextButton(onClick = { playChannel(channel, channel.defaultChannelId) }) {
                                                                Text("Play", color = MaxStreamTheme.CrimsonAccent)
                                                            }
                                                            TextButton(onClick = {
                                                                JioTvRepo.removeManualMapping(context, mapping.channelId)
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
                                        Button(
                                            onClick = {
                                                onPlayRequested(entry.url, entry.name, emptyMap())
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaxStreamTheme.CrimsonAccent)
                                        ) { Text("PLAY", color = Color.White) }

                                        OutlinedButton(
                                            onClick = {
                                                linkSearchQuery = JioTvRepo.generateSmartFilterKeyword(entry.name)
                                                m3uToLink = entry
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("LINK", color = primaryTextColor) }

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
                                            Text(if (isTesting) "..." else "TEST", color = primaryTextColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Featured Hero Live Carousel Banner for CineTV
 */
@Composable
private fun CineTvHeroBanner(
    featuredChannels: List<LiveChannelItem>,
    onPlayChannel: (LiveChannelItem) -> Unit
) {
    if (featuredChannels.isEmpty()) return
    val currentChannel = featuredChannels.first()
    val isDark = isSystemInDarkTheme()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isDark) MaxStreamTheme.MidnightSurface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(175.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onPlayChannel(currentChannel) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient Backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                if (isDark) Color(0xFF0D0E15) else MaterialTheme.colorScheme.surfaceVariant,
                                if (isDark) Color(0xFF1E2235) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel Logo in glowing capsule
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.White,
                    border = BorderStroke(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = currentChannel.logoUrl,
                            contentDescription = currentChannel.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // LIVE Pulsing Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaxStreamTheme.CrimsonAccent
                        ) {
                            Text(
                                text = "🔴 LIVE NOW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = currentChannel.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = currentChannel.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        ),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Instant 1080p Low-Latency Stream",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onPlayChannel(currentChannel) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaxStreamTheme.CrimsonAccent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch Live", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

/**
 * Categorized Live Channel Rail showing 15 items + "See all"
 */
@Composable
private fun CineTvCategoryRail(
    category: String,
    channels: List<LiveChannelItem>,
    smartCache: Map<String, ChannelCacheEntry>,
    selectedLanguage: String,
    onSeeAllClick: () -> Unit,
    onPlayChannel: (LiveChannelItem, String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    val displayChannels = remember(channels) { channels.take(15) }
    val hasMore = channels.size > 15

    Column(modifier = Modifier.fillMaxWidth()) {
        // Rail Title Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaxStreamTheme.CrimsonAccent)
                )
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = primaryTextColor
                )
            }

            if (hasMore) {
                TextButton(
                    onClick = onSeeAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "See all (${channels.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaxStreamTheme.CrimsonAccent
                        )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaxStreamTheme.CrimsonAccent
                    )
                }
            }
        }

        // Horizontal Row of Channel Cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(displayChannels) { channel ->
                val channelLangId = channel.getIdForLanguage(if (selectedLanguage != "All Languages") selectedLanguage else channel.defaultLanguage)
                val entry = smartCache[channelLangId]

                LiveTvGridChannelCard(
                    channel = channel,
                    currentActiveId = channelLangId,
                    isM3uFallback = entry?.preferredSource == PlaybackSource.M3U || entry?.isManualMapping == true,
                    onPlayRequested = { id -> onPlayChannel(channel, id) }
                )
            }

            if (hasMore) {
                item {
                    // "See All" Glass Action Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .width(130.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onSeeAllClick)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "See all",
                                        tint = MaxStreamTheme.CrimsonAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "See All",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = primaryTextColor
                            )
                            Text(
                                text = "+${channels.size - 15} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact Live TV Grid Channel Card (used in rails and full grid)
 */
@Composable
fun LiveTvGridChannelCard(
    channel: LiveChannelItem,
    currentActiveId: String,
    isM3uFallback: Boolean,
    onPlayRequested: (channelId: String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val isPaid = globalPaidChannels[currentActiveId] == true

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .width(135.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlayRequested(currentActiveId) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top badges (LIVE dot / Paid / Synced)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaxStreamTheme.CrimsonAccent
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (isPaid) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "PAID",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else if (isM3uFallback) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaxStreamTheme.ElectricCyan.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "M3U",
                            fontSize = 8.sp,
                            color = MaxStreamTheme.ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Channel Logo in centered box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.White,
                modifier = Modifier.size(52.dp)
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Channel Title & Language
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryTextColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Standard Live Channel Row Item with Expandable EPG schedule
 */
@Composable
fun LiveChannelRowItem(
    channel: LiveChannelItem,
    currentActiveId: String,
    isM3uFallback: Boolean,
    confidenceScore: Int,
    isManualMapping: Boolean,
    onPlayRequested: (channelId: String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline
    val elevatedSurface = if (isDark) MaxStreamTheme.ElevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val glassBorder = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val isPaid = globalPaidChannels[currentActiveId] == true
    val context = LocalContext.current
    var epgExpanded by remember { mutableStateOf(false) }
    var epgProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var epgLoading by remember { mutableStateOf(false) }

    LaunchedEffect(epgExpanded, currentActiveId) {
        if (epgExpanded && epgProgram == null && !epgLoading) {
            epgLoading = true
            epgProgram = JioTvRepo.fetchEpg(context, currentActiveId)
            epgLoading = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlayRequested(currentActiveId) },
        shape = RoundedCornerShape(16.dp),
        color = elevatedSurface,
        border = BorderStroke(1.dp, glassBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.title,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color.Black.copy(alpha = 0.3f) else Color.White)
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        channel.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "${channel.category} • ${channel.variants.find { it.channelId == currentActiveId }?.language ?: channel.defaultLanguage}",
                            fontSize = 11.sp,
                            color = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isPaid) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    " 🟡 Paid ",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                        if (isM3uFallback) {
                            Surface(
                                color = MaxStreamTheme.ElectricCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    " ✓ Synced ",
                                    fontSize = 9.sp,
                                    color = MaxStreamTheme.ElectricCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = { epgExpanded = !epgExpanded }) {
                    Icon(
                        imageVector = if (epgExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "EPG",
                        tint = secondaryTextColor
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaxStreamTheme.CrimsonAccent,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(visible = epgExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(14.dp)
                ) {
                    if (epgLoading) {
                        Text("Loading EPG schedule...", fontSize = 12.sp, color = secondaryTextColor)
                    } else if (epgProgram != null) {
                        val prog = epgProgram!!
                        Text(
                            prog.showname,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            prog.description,
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    prog.showtime,
                                    fontSize = 11.sp,
                                    color = MaxStreamTheme.CrimsonAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        Text("No EPG schedule available for this channel.", fontSize = 12.sp, color = mutedTextColor)
                    }
                }
            }
        }
    }
}

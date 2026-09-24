package xyz.mpv.rex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import xyz.mpv.rex.ui.browser.components.FloatingBottomNav
import xyz.mpv.rex.ui.browser.medialibrary.MediaLibraryContent
import xyz.mpv.rex.ui.browser.components.NavTabItem
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.dialogs.CommunityLinksDialog
import xyz.mpv.rex.ui.browser.cinehub.CineHubScreen
import xyz.mpv.rex.ui.browser.folderlist.FolderListScreen
import xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.browser.shorts.ShortsScreen
import xyz.mpv.rex.ui.browser.selection.SelectionManager
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayer
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerDefaults
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object MainScreen : Screen {
  // Use a companion object to store state more persistently
  private var persistentSelectedTab: Int = 0
  private var persistentPreviousTab: Int = 0
  
  private val _tabRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1)
  val tabRequest = _tabRequest.asSharedFlow()

  private val _scrollToTopRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val scrollToTopRequest = _scrollToTopRequest.asSharedFlow()

  fun requestTab(tab: Int) {
    _tabRequest.tryEmit(tab)
  }
  
  fun requestPreviousTab() {
    _tabRequest.tryEmit(persistentPreviousTab)
  }

  // Shared state that can be updated by FileSystemBrowserScreen
  private val _isInSelectionModeShared = MutableStateFlow(false)
  val isInSelectionModeShared = _isInSelectionModeShared.asStateFlow()
  
  private val _shouldHideNavigationBar = MutableStateFlow(false)
  val shouldHideNavigationBar = _shouldHideNavigationBar.asStateFlow()
  
  private val _isBrowserBottomBarVisible = MutableStateFlow(false)
  val isBrowserBottomBarVisible = _isBrowserBottomBarVisible.asStateFlow()
  
  private val _sharedVideoSelectionManager = MutableStateFlow<Any?>(null)
  val sharedVideoSelectionManager = _sharedVideoSelectionManager.asStateFlow()
  
  // Check if the selection contains only videos and update navigation bar visibility accordingly
  private val _onlyVideosSelected = MutableStateFlow(false)
  val onlyVideosSelected = _onlyVideosSelected.asStateFlow()
  
  // Track when permission denied screen is showing to hide FAB
  private val _isPermissionDenied = MutableStateFlow(false)
  val isPermissionDenied = _isPermissionDenied.asStateFlow()
  
  /**
   * Update selection state and navigation bar visibility
   * This method should be called whenever selection changes
   */
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?
  ) {
    _isInSelectionModeShared.value = isInSelectionMode
    _onlyVideosSelected.value = isOnlyVideosSelected
    _sharedVideoSelectionManager.value = selectionManager
    
    // Only hide navigation bar when videos are selected AND in selection mode
    // This fixes the issue where bottom bar disappears when only videos are selected
    _shouldHideNavigationBar.value = isInSelectionMode && isOnlyVideosSelected
  }
  
  /**
   * Update permission state to control FAB visibility
   */
  fun updatePermissionState(isDenied: Boolean) {
    _isPermissionDenied.value = isDenied
  }

  /**
   * Get current permission denied state
   */
  fun getPermissionDeniedState(): Boolean = _isPermissionDenied.value

  /**
   * Update bottom navigation bar visibility based on floating bottom bar state
   */
  fun updateBottomBarVisibility(shouldShow: Boolean) {
    // Hide bottom navigation when floating bottom bar is visible
    _shouldHideNavigationBar.value = !shouldShow
  }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    android.util.Log.d("TRANSITION_TRACE", "MainScreen.Content() started")
    android.util.Log.d("APP_STARTUP", "APP_STAGE_4_START_DESTINATION")
    androidx.compose.runtime.LaunchedEffect(Unit) { android.util.Log.d("APP_STARTUP", "APP_STAGE_5_HOME_SCREEN_RENDERED") }
    var selectedTab by remember {
      mutableIntStateOf(persistentSelectedTab)
    }
    
    var previousTab by remember {
      mutableIntStateOf(persistentPreviousTab)
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val browserPreferences = koinInject<BrowserPreferences>()
    val miniPlayerStateManager = koinInject<MiniPlayerStateManager>()
    val miniPlayerState by miniPlayerStateManager.state.collectAsState()
    val isShortsEnabled by browserPreferences.enableShorts.collectAsState()
    val enableTabRecents by browserPreferences.enableTabRecents.collectAsState()
    val enableTabPlaylists by browserPreferences.enableTabPlaylists.collectAsState()
    val enableTabNetwork by browserPreferences.enableTabNetwork.collectAsState()
    val enableTabCineHub by browserPreferences.enableTabCineHub.collectAsState()
    val enableTabCineTv by browserPreferences.enableTabCineTv.collectAsState()
    val enableCineHubIntegration by browserPreferences.enableCineHubIntegration.collectAsState()

    val homeLabel = "Files"
    val shortsLabel = stringResource(R.string.shorts)
    val cineHubLabel = stringResource(R.string.cinehub)
    val cineTvLabel = stringResource(R.string.cinetv)
    val recentsLabel = stringResource(R.string.recents)
    val playlistsLabel = stringResource(R.string.playlists)
    val networkLabel = stringResource(R.string.network)

    val isCineHubTabVisible = enableTabCineHub && enableCineHubIntegration

    val visibleTabs = remember(
      isShortsEnabled, isCineHubTabVisible, enableTabCineTv, enableTabRecents, enableTabPlaylists, enableTabNetwork,
      homeLabel, shortsLabel, cineHubLabel, cineTvLabel, recentsLabel, playlistsLabel, networkLabel
    ) {
      buildList {
        add(
          VisibleTab("library", "Local Library", icon = Icons.Rounded.Folder) {
            MediaLibraryContent()
          }
        )
        if (isCineHubTabVisible) {
          add(
            VisibleTab("cinehub", cineHubLabel, iconResId = R.drawable.ic_max_stream_mark) {
              CineHubScreen.Content()
            }
          )
        }
        if (enableTabCineTv) {
          add(
            VisibleTab("cinetv", cineTvLabel, icon = Icons.Rounded.Tv) {
              xyz.mpv.rex.cinetv.ui.LiveTvTabScreen(
                searchQuery = "",
                onPlayRequested = { streamUrl, title, meta ->
                  val uri = android.net.Uri.parse(streamUrl)
                  val playerIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    setClass(context, xyz.mpv.rex.ui.player.PlayerActivity::class.java)
                    putExtra("internal_launch", true)
                    putExtra("launch_source", "cinetv")
                    putExtra("title", title)
                    putExtra("filename", title)
                    putExtra("cinetv_source_type", meta["SourceType"])
                    putExtra("cinetv_poster", meta["Logo"])
                    setDataAndType(uri, "video/*")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                  context.startActivity(playerIntent)
                }
              )
            }
          )
        }
        if (enableTabRecents) {
          add(
            VisibleTab("recents", recentsLabel, icon = Icons.Rounded.History) {
              RecentlyPlayedScreen.Content()
            }
          )
        }
        if (enableTabPlaylists) {
          add(
            VisibleTab("playlists", playlistsLabel, icon = Icons.AutoMirrored.Rounded.PlaylistPlay) {
              PlaylistScreen.Content()
            }
          )
        }
        if (enableTabNetwork) {
          add(
            VisibleTab("network", networkLabel, icon = Icons.Rounded.Language) {
              NetworkStreamingScreen.Content()
            }
          )
        }
      }
    }

    // Ensure selectedTab is always clamped within active tabs range
    LaunchedEffect(visibleTabs) {
      if (selectedTab >= visibleTabs.size) {
        selectedTab = 0
      }
    }

    // Intercept back button when on Shorts tab to return to previous tab
    val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
    androidx.activity.compose.BackHandler(enabled = shortsIdx != -1 && selectedTab == shortsIdx) {
      selectedTab = previousTab
    }

    // Shared state (across the app) collected reactively via StateFlow
    val isInSelectionMode by _isInSelectionModeShared.collectAsState()
    val hideNavigationBar by _shouldHideNavigationBar.collectAsState()
    val rawSelectionManager by _sharedVideoSelectionManager.collectAsState()
    val videoSelectionManager = rawSelectionManager as? SelectionManager<*, *>

    // Update persistent state whenever tab changes
    LaunchedEffect(selectedTab) {
      if (selectedTab != persistentSelectedTab) {
        previousTab = persistentSelectedTab
        persistentPreviousTab = previousTab
      }
      android.util.Log.d("MainScreen", "selectedTab changed to: $selectedTab (was ${persistentSelectedTab}), previousTab is $previousTab")
      persistentSelectedTab = selectedTab
    }

    // Handle tab requests from other screens
    LaunchedEffect(Unit) {
      tabRequest.collect { tab ->
        selectedTab = tab
      }
    }

    // Community Hub auto-popup: Phase 1 (initial 1 min test threshold) / Phase 2 (15 days after "Already joined")
    val appearancePreferences = koinInject<AppearancePreferences>()
    val enableModernGlassUI by appearancePreferences.enableModernGlassUI.collectAsState()
    val isCommunityPromptPermanentlyDismissed by appearancePreferences.communityPromptDismissedPermanently.collectAsState()
    val firstOpenTimestamp by appearancePreferences.communityFirstAppOpenTimestamp.collectAsState()
    val alreadyJoinedTimestamp by appearancePreferences.communityAlreadyJoinedTimestamp.collectAsState()
    var showCommunityAutoPopup by remember { mutableStateOf(false) }
    var isFollowupPrompt by remember { mutableStateOf(false) }
    var hasDismissedThisSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      if (firstOpenTimestamp == 0L) {
        appearancePreferences.communityFirstAppOpenTimestamp.set(System.currentTimeMillis())
      }
    }

    val isHomeTabActive = selectedTab in visibleTabs.indices && visibleTabs[selectedTab].id == "home"

    LaunchedEffect(
      isHomeTabActive,
      isCommunityPromptPermanentlyDismissed,
      hasDismissedThisSession,
      firstOpenTimestamp,
      alreadyJoinedTimestamp
    ) {
      if (isHomeTabActive && !isCommunityPromptPermanentlyDismissed && !hasDismissedThisSession) {
        val (remainingDelay, isFollowup) = if (alreadyJoinedTimestamp > 0L) {
          // Phase 2: User clicked "Already joined" in the past -> wait 10 days before follow-up
          val followupCooldownMs = 10L * 24 * 60 * 60 * 1000L // 10 days
          val elapsed = System.currentTimeMillis() - alreadyJoinedTimestamp
          val remaining = (followupCooldownMs - elapsed).coerceAtLeast(0L)
          remaining to true
        } else {
          // Phase 1: User hasn't clicked "Already joined" yet -> wait initial threshold (20 minutes)
          val initialDelayMs = 20 * 60 * 1000L // 20 minutes
          val currentFirstOpen = if (firstOpenTimestamp == 0L) System.currentTimeMillis() else firstOpenTimestamp
          val elapsed = System.currentTimeMillis() - currentFirstOpen
          val remaining = (initialDelayMs - elapsed).coerceAtLeast(0L)
          remaining to false
        }

        if (remainingDelay > 0L) {
          kotlinx.coroutines.delay(remainingDelay)
        }

        if (isHomeTabActive && !isCommunityPromptPermanentlyDismissed && !hasDismissedThisSession) {
          isFollowupPrompt = isFollowup
          showCommunityAutoPopup = true
        }
      }
    }

    // Scaffold with bottom navigation bar
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        // Animated bottom navigation bar with slide animations
        // Also hide if Shorts tab is active (index 1 when enabled, index -1 when disabled)
        val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
        val isShortsTabActive = isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx
        
        AnimatedVisibility(
            visible = !hideNavigationBar && !isShortsTabActive && visibleTabs.size > 1,
            enter = slideInVertically(
              animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
              ),
              initialOffsetY = { fullHeight -> fullHeight }
            ),
            exit = slideOutVertically(
              animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
              ),
              targetOffsetY = { fullHeight -> fullHeight }
            )
          ) {
            if (enableModernGlassUI) {
              FloatingBottomNav(
                tabs = visibleTabs.map { NavTabItem(id = it.id, label = it.label, icon = it.icon, iconResId = it.iconResId) },
                selectedTab = selectedTab,
                onTabSelected = { index ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  if (selectedTab == index) {
                    _scrollToTopRequest.tryEmit(visibleTabs[index].id)
                  } else {
                    selectedTab = index
                  }
                },
                modifier = Modifier
                  .navigationBarsPadding()
                  .padding(bottom = 12.dp)
              )
            } else {
              NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
              ) {
                visibleTabs.forEachIndexed { index, tab ->
                  NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = {
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      if (selectedTab == index) {
                        _scrollToTopRequest.tryEmit(visibleTabs[index].id)
                      } else {
                        selectedTab = index
                      }
                    },
                    icon = {
                      if (tab.iconResId != null) {
                        androidx.compose.foundation.Image(
                          painter = androidx.compose.ui.res.painterResource(tab.iconResId),
                          contentDescription = tab.label,
                          modifier = Modifier.size(24.dp)
                        )
                      } else if (tab.icon != null) {
                        Icon(tab.icon, contentDescription = tab.label)
                      }
                    },
                    label = { Text(tab.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    alwaysShowLabel = false
                  )
                }
              }
            }
          }
        }
    ) { paddingValues ->
      Box(modifier = Modifier.fillMaxSize()) {
        val fabBottomPadding = 80.dp

        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            val slideDistance = with(density) { 48.dp.roundToPx() }
            val animationDuration = 250
            
            if (targetState > initialState) {
              (slideInHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                initialOffsetX = { slideDistance }
              ) + fadeIn(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                )
              )) togetherWith (slideOutHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                targetOffsetX = { -slideDistance }
              ) + fadeOut(
                animationSpec = tween(
                  durationMillis = animationDuration / 2,
                  easing = FastOutSlowInEasing
                )
              ))
            } else {
              (slideInHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                initialOffsetX = { -slideDistance }
              ) + fadeIn(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                )
              )) togetherWith (slideOutHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                targetOffsetX = { slideDistance }
              ) + fadeOut(
                animationSpec = tween(
                  durationMillis = animationDuration / 2,
                  easing = FastOutSlowInEasing
                )
              ))
            }
          },
          label = "tab_animation"
        ) { targetTab ->
          val shortsIdx = visibleTabs.indexOfFirst { it.id == "shorts" }
          val isShortsTabActive = isShortsEnabled && shortsIdx != -1 && selectedTab == shortsIdx
          val isNavBarVisible = !hideNavigationBar && !isShortsTabActive && visibleTabs.size > 1
          
          val navBarHeight = if (isNavBarVisible) paddingValues.calculateBottomPadding().coerceAtLeast(80.dp) else 0.dp
          val miniPlayerHeight = if (miniPlayerState.isPlaybackActive) MiniPlayerDefaults.CompactHeight else 0.dp
          val totalBottomPadding = navBarHeight + miniPlayerHeight
          
          CompositionLocalProvider(
            LocalNavigationBarHeight provides totalBottomPadding
          ) {
            if (targetTab in visibleTabs.indices) {
              visibleTabs[targetTab].content()
            } else {
              FolderListScreen.Content()
            }
          }
        }
      }
    }

    if (showCommunityAutoPopup && !isCommunityPromptPermanentlyDismissed) {
      CommunityLinksDialog(
        isFollowupPrompt = isFollowupPrompt,
        onDismissRequest = {
          hasDismissedThisSession = true
          showCommunityAutoPopup = false
        }
      )
    }

  }
}

// CompositionLocal for navigation bar height
val LocalNavigationBarHeight = compositionLocalOf { 0.dp }

private data class VisibleTab(
  val id: String,
  val label: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  val iconResId: Int? = null,
  val content: @Composable () -> Unit
)

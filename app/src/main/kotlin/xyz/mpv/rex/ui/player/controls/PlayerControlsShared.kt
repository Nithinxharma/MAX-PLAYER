package xyz.mpv.rex.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Brush
import xyz.mpv.rex.cinehub.data.NfoScanner
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface as M3Surface
import xyz.mpv.rex.ui.player.controls.components.glassSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import xyz.mpv.rex.cinehub.data.ActiveMediaResolution
import xyz.mpv.rex.cinehub.data.CineOnlineScraper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.PlayerButton
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.player.Panels
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.ui.player.VideoAspect
import xyz.mpv.rex.ui.player.controls.components.ControlsButton
import xyz.mpv.rex.ui.player.controls.components.CurrentChapter
import xyz.mpv.rex.ui.theme.controlColor
import xyz.mpv.rex.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun RenderPlayerButton(
  button: PlayerButton,
  chapters: List<Segment>,
  currentChapter: Int?,
  isPortrait: Boolean,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: xyz.mpv.rex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  buttonSize: Dp = 40.dp,
  isMoreSheet: Boolean = false,
) {
  val appearancePreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
  val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()
  val enableExpandingControls by appearancePreferences.enableExpandingOnScreenControls.collectAsState()
  
  val surfaceColor = when {
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }
  
  val contentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  }
  
  val activeSurfaceColor = when {
    hideBackground -> Color.Transparent
    matchTheme -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
  }
  
  val activeContentColor = when {
    matchTheme -> {
      if (hideBackground) MaterialTheme.colorScheme.secondary
      else MaterialTheme.colorScheme.onSecondaryContainer
    }
    else -> MaterialTheme.colorScheme.primary
  }
  
  val borderColor = if (hideBackground) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
  )
  
  val activeBorderColor = if (hideBackground) null else BorderStroke(
    1.dp,
    if (matchTheme) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
  )

  val clickEvent = LocalPlayerButtonsClickEvent.current
  when (button) {
    PlayerButton.BACK_ARROW -> {
      ControlsButton(
        icon = Icons.AutoMirrored.Default.ArrowBack,
        onClick = onBackPress,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.VIDEO_TITLE -> {
      val playlistModeEnabled = viewModel.hasPlaylistSupport()

      val titleInteractionSource = remember { MutableInteractionSource() }

      Surface(
        shape = CircleShape,
        color = surfaceColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = borderColor,
        modifier =
          Modifier
            .height(buttonSize)
            .clip(CircleShape)
            .clickable(
              interactionSource = titleInteractionSource,
              indication = ripple(
                bounded = true,
              ),
              enabled = playlistModeEnabled,
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.Playlist)
              },
            ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .padding(
                horizontal = MaterialTheme.spacing.smaller,
                vertical = MaterialTheme.spacing.smaller,
              ),        ) {
          Text(
            mediaTitle ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false),
          )
          viewModel.getPlaylistInfo()?.let { playlistInfo ->
            Text(
              stringResource(R.string.playlist_separator, playlistInfo),
              maxLines = 1,
              overflow = TextOverflow.Visible,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }

    PlayerButton.BOOKMARKS_CHAPTERS -> {
      if (chapters.isNotEmpty()) {
        val nextChapter = (currentChapter ?: 0) + 1
        val onNextChapter: (() -> Unit)? = if (nextChapter < chapters.size) {
          { `is`.xyz.mpv.MPVLib.setPropertyInt("chapter", nextChapter) }
        } else null

        if (isMoreSheet) {
          val chapter = chapters.getOrNull(currentChapter ?: 0)
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .combinedClickable(
                onClick = { onOpenSheet(Sheets.Chapters) },
                onDoubleClick = onNextChapter,
              )
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Bookmarks,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = chapter?.name ?: stringResource(R.string.chapters),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
              )
            }
          }
        } else if (enableExpandingControls) {
          ExpandingChaptersButton(
            chapters = chapters,
            currentChapter = currentChapter,
            onOpenSheet = onOpenSheet,
            buttonSize = buttonSize,
            modifier = Modifier
          )
        } else {
          ControlsButton(
            Icons.Default.Bookmarks,
            onClick = { onOpenSheet(Sheets.Chapters) },
            onDoubleClick = onNextChapter,
            modifier = Modifier.size(buttonSize),
          )
        }
      }
    }

    PlayerButton.PLAYBACK_SPEED -> {
      val cycleSpeed = {
        val newSpeed = if (playbackSpeed >= 2f) 0.25f else playbackSpeed + 0.25f
        `is`.xyz.mpv.MPVLib.setPropertyFloat("speed", newSpeed)
      }

      val showText = isSpeedNonOne || isMoreSheet

      if (isMoreSheet) {
        @OptIn(ExperimentalFoundationApi::class)
        Surface(
          shape = CircleShape,
          color = if (isSpeedNonOne) activeSurfaceColor else surfaceColor,
          contentColor = if (isSpeedNonOne) activeContentColor else contentColor,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = if (isSpeedNonOne) activeBorderColor else borderColor,
          modifier = Modifier
            .height(buttonSize)
            .animateContentSize()
            .clip(CircleShape)
            .combinedClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                cycleSpeed()
              },
              onLongClick = {
                clickEvent()
                onOpenSheet(Sheets.PlaybackSpeed)
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
              horizontal = MaterialTheme.spacing.smaller,
              vertical = MaterialTheme.spacing.smaller,
            ),
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = stringResource(R.string.playback_speed),
              tint = if (isSpeedNonOne) activeContentColor else contentColor,
              modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(
              visible = showText,
              enter = fadeIn() + expandHorizontally(),
              exit = fadeOut() + shrinkHorizontally()
            ) {
              Text(
                text = String.format("%.2fx", playbackSpeed),
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
              )
            }
          }
        }
      } else {
        ControlsButton(
          icon = Icons.Default.Speed,
          onClick = {
            clickEvent()
            cycleSpeed()
          },
          onLongClick = {
            clickEvent()
            onOpenSheet(Sheets.PlaybackSpeed)
          },
          color = if (isSpeedNonOne) MaterialTheme.colorScheme.primary else null,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.DECODER -> {
      if (isMoreSheet) {
        Surface(
          shape = CircleShape,
          color = surfaceColor,
          contentColor = contentColor,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = borderColor,
          modifier = Modifier
            .height(buttonSize)
            .clip(CircleShape)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.Decoders)
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier
                .padding(
                  horizontal = MaterialTheme.spacing.small,
                  vertical = MaterialTheme.spacing.smaller,
                ),
          ) {
            Icon(
              imageVector = Icons.Outlined.Memory,
              contentDescription = null,
              modifier = Modifier.size(24.dp).padding(end = 6.dp)
            )
            Text(
              text = decoder.title,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      } else {
        ControlsButton(
          icon = Icons.Outlined.Memory,
          onClick = {
            clickEvent()
            onOpenSheet(Sheets.Decoders)
          },
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.SCREEN_ROTATION -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.cycleScreenRotations() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.screen_rotation),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.ScreenRotation,
            onClick = viewModel::cycleScreenRotations,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.FRAME_NAVIGATION -> {
      val isExpanded by viewModel.isFrameNavigationExpanded.collectAsState()
      val isActive = isExpanded

      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable {
                viewModel.toggleFrameNavigationExpanded()
                onOpenSheet(Sheets.None)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.frame_nav),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
        Surface(
          shape = CircleShape,
          color = if (isActive) activeSurfaceColor else surfaceColor,
          border = if (isActive) activeBorderColor else borderColor,
          modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = viewModel::toggleFrameNavigationExpanded),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Camera,
              contentDescription = stringResource(R.string.frame_navigation),
              tint = if (isActive) activeContentColor else contentColor,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }
    }

    PlayerButton.PICTURE_IN_PICTURE -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { activity.enterPipModeHidingOverlay() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.PictureInPictureAlt,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.pip),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.PictureInPictureAlt,
            onClick = { activity.enterPipModeHidingOverlay() },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.ASPECT_RATIO -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable {
                  when (aspect) {
                    VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
                    VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
                    VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
                  }
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = when (aspect) {
                    VideoAspect.Fit -> Icons.Default.AspectRatio
                    VideoAspect.Stretch -> Icons.Default.ZoomOutMap
                    VideoAspect.Crop -> Icons.Default.FitScreen
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = aspect.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon =
              when (aspect) {
                VideoAspect.Fit -> Icons.Default.AspectRatio
                VideoAspect.Stretch -> Icons.Default.ZoomOutMap
                VideoAspect.Crop -> Icons.Default.FitScreen
              },
            onClick = {
              when (aspect) {
                VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
                VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
                VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
              }
            },
            onLongClick = { onOpenSheet(Sheets.AspectRatios) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.VIDEO_ZOOM -> {
      val isZoomed = kotlin.math.abs(currentZoom) >= 0.005f
      if (isMoreSheet) {
        @OptIn(ExperimentalFoundationApi::class)
        Surface(
          shape = CircleShape,
          color = if (isZoomed) activeSurfaceColor else surfaceColor,
          contentColor = if (isZoomed) activeContentColor else contentColor,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = if (isZoomed) activeBorderColor else borderColor,
          modifier = Modifier
            .height(buttonSize)
            .clip(CircleShape)
            .combinedClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.VideoZoom)
              },
              onLongClick = {
                clickEvent()
                viewModel.setVideoZoom(0f)
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(
              horizontal = MaterialTheme.spacing.small,
              vertical = MaterialTheme.spacing.small,
            ),
          ) {
            Icon(
              imageVector = Icons.Default.ZoomIn,
              contentDescription = stringResource(R.string.video_zoom),
              tint = if (isZoomed) activeContentColor else contentColor,
              modifier = Modifier.size(24.dp),
            )
            Text(
              text = String.format("%.0f%%", currentZoom * 100),
              maxLines = 1,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      } else {
        ControlsButton(
          Icons.Default.ZoomIn,
          onClick = {
            clickEvent()
            onOpenSheet(Sheets.VideoZoom)
          },
          onLongClick = {
            clickEvent()
            viewModel.setVideoZoom(0f)
          },
          color = if (isZoomed) MaterialTheme.colorScheme.primary else null,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.LOCK_CONTROLS -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.lockControls() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.lock),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.LockOpen,
            onClick = viewModel::lockControls,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.AUDIO_TRACK -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.AudioTracks) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.audio),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else if (enableExpandingControls) {
          ExpandingAudioButton(
            viewModel = viewModel,
            onOpenSheet = onOpenSheet,
            onOpenPanel = onOpenPanel,
            buttonSize = buttonSize,
            modifier = Modifier
          )
      } else {
          ControlsButton(
            Icons.Default.Audiotrack,
            onClick = { onOpenSheet(Sheets.AudioTracks) },
            onLongClick = { onOpenPanel(Panels.AudioDelay) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.SUBTITLES -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.SubtitleTracks) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.subtitles),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else if (enableExpandingControls) {
          ExpandingSubtitleButton(
            viewModel = viewModel,
            onOpenSheet = onOpenSheet,
            onOpenPanel = onOpenPanel,
            buttonSize = buttonSize,
            modifier = Modifier
          )
      } else {
          ControlsButton(
            Icons.Default.Subtitles,
            onClick = { onOpenSheet(Sheets.SubtitleTracks) },
            onLongClick = { onOpenPanel(Panels.SubtitleDelay) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.MORE_OPTIONS -> {
      if (isMoreSheet) {
          // Hide more options inside more options sheet
      } else {
          ControlsButton(
            Icons.Default.MoreVert,
            onClick = { onOpenSheet(Sheets.More) },
            onLongClick = { onOpenPanel(Panels.VideoFilters) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.CURRENT_CHAPTER -> {
      if (isPortrait || isMoreSheet) {
      } else if (enableExpandingControls && chapters.isNotEmpty()) {
        ExpandingChaptersButton(
          chapters = chapters,
          currentChapter = currentChapter,
          onOpenSheet = onOpenSheet,
          buttonSize = buttonSize,
          modifier = Modifier
        )
      } else {
        AnimatedVisibility(
          chapters.getOrNull(currentChapter ?: 0) != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          chapters.getOrNull(currentChapter ?: 0)?.let { chapter ->
            val nextChapter = (currentChapter ?: 0) + 1
            val onNextChapter: (() -> Unit)? = if (nextChapter < chapters.size) {
              { `is`.xyz.mpv.MPVLib.setPropertyInt("chapter", nextChapter) }
            } else null

            CurrentChapter(
              chapter = chapter,
              onClick = { onOpenSheet(Sheets.Chapters) },
              onDoubleClick = onNextChapter,
            )
          }
        }
      }
    }

    PlayerButton.REPEAT_MODE -> {
      val repeatMode by viewModel.repeatMode.collectAsState()
      val icon = when (repeatMode) {
        xyz.mpv.rex.ui.player.RepeatMode.OFF -> Icons.Default.Repeat
        xyz.mpv.rex.ui.player.RepeatMode.ONE -> Icons.Default.RepeatOne
        xyz.mpv.rex.ui.player.RepeatMode.ALL -> Icons.Default.RepeatOn
      }
      val isActive = repeatMode != xyz.mpv.rex.ui.player.RepeatMode.OFF
      
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.cycleRepeatMode() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = when (repeatMode) {
                    xyz.mpv.rex.ui.player.RepeatMode.OFF -> stringResource(R.string.repeat_off)
                    xyz.mpv.rex.ui.player.RepeatMode.ONE -> stringResource(R.string.repeat_one)
                    xyz.mpv.rex.ui.player.RepeatMode.ALL -> stringResource(R.string.repeat_all)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = icon,
            onClick = viewModel::cycleRepeatMode,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.CUSTOM_SKIP -> {
      val playerPreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.PlayerPreferences>()
      val customSkipDuration by playerPreferences.customSkipDuration.collectAsState()
      if (isMoreSheet) {
          @OptIn(ExperimentalFoundationApi::class)
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .combinedClickable(
                onClick = {
                  clickEvent()
                  viewModel.seekBy(customSkipDuration)
                },
                onLongClick = {
                  clickEvent()
                  onOpenSheet(Sheets.CustomSkipDuration)
                }
              )
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.skip_seconds, customSkipDuration),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.FastForward,
            onClick = { viewModel.seekBy(customSkipDuration) },
            onLongClick = {
              clickEvent()
              onOpenSheet(Sheets.CustomSkipDuration)
            },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.SHUFFLE -> {
      // Only show shuffle button if there's a playlist (more than one video)
      if (viewModel.hasPlaylistSupport()) {
        val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
        
        if (isMoreSheet) {
            Surface(
              shape = CircleShape,
              color = if (shuffleEnabled) activeSurfaceColor else surfaceColor,
              contentColor = if (shuffleEnabled) activeContentColor else contentColor,
              border = if (shuffleEnabled) activeBorderColor else borderColor,
              modifier = Modifier
                .height(buttonSize)
                .clip(CircleShape)
                .clickable { viewModel.toggleShuffle() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
              ) {
                Icon(
                  imageVector = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  text = if (shuffleEnabled) stringResource(R.string.shuffle_on) else stringResource(R.string.shuffle_off),
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
              }
            }
        } else {
            ControlsButton(
              icon = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
              onClick = viewModel::toggleShuffle,
              modifier = Modifier.size(buttonSize),
            )
        }
      }
    }

    PlayerButton.MIRROR -> {
      val isMirrored by viewModel.isMirrored.collectAsState()
      
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (isMirrored) activeSurfaceColor else surfaceColor,
            contentColor = if (isMirrored) activeContentColor else contentColor,
            border = if (isMirrored) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.toggleMirroring() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = if (isMirrored) stringResource(R.string.mirrored) else stringResource(R.string.mirror),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.Flip,
            onClick = viewModel::toggleMirroring,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.VERTICAL_FLIP -> {
      val isVerticalFlipped by viewModel.isVerticalFlipped.collectAsState()
      
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (isVerticalFlipped) activeSurfaceColor else surfaceColor,
            contentColor = if (isVerticalFlipped) activeContentColor else contentColor,
            border = if (isVerticalFlipped) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.toggleVerticalFlip() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(90f)
              )
              Text(
                text = if (isVerticalFlipped) stringResource(R.string.flipped) else stringResource(R.string.flip_vertical),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          Surface(
            shape = CircleShape,
            color = if (isVerticalFlipped) activeSurfaceColor else surfaceColor,
            contentColor = if (isVerticalFlipped) activeContentColor else contentColor,
            border = if (isVerticalFlipped) activeBorderColor else borderColor,
            modifier = Modifier
              .size(buttonSize)
              .clip(CircleShape)
              .clickable(onClick = viewModel::toggleVerticalFlip),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = stringResource(R.string.flip_vertical_desc),
                tint = if (isVerticalFlipped) activeContentColor else contentColor,
                modifier = Modifier
                  .padding(MaterialTheme.spacing.smaller)
                  .size(24.dp)
                  .rotate(90f),
              )
            }
          }
      }
    }

    PlayerButton.AB_LOOP -> {
      val isExpanded by viewModel.isABLoopExpanded.collectAsState()
      val loopA by viewModel.abLoopA.collectAsState()
      val loopB by viewModel.abLoopB.collectAsState()
      val isActive = loopA != null || loopB != null || isExpanded

      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable {
                viewModel.toggleABLoopExpanded()
                onOpenSheet(Sheets.None)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Text(
                text = stringResource(R.string.ab_loop_short),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              )
              Text(
                text = stringResource(R.string.loop),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
        Surface(
          shape = CircleShape,
          color = if (isActive) activeSurfaceColor else surfaceColor,
          border = if (isActive) activeBorderColor else borderColor,
          modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = viewModel::toggleABLoopExpanded),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = stringResource(R.string.ab_loop_short),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              color = if (isActive) activeContentColor else contentColor,
            )
          }
        }
      }
    }

    PlayerButton.BACKGROUND_PLAYBACK -> {
      val audioPreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AudioPreferences>()
      val automaticBackgroundPlayback by audioPreferences.automaticBackgroundPlayback.collectAsState()
      val icon = if (automaticBackgroundPlayback) Icons.Default.Headset else Icons.Default.HeadsetOff

      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .combinedClickable(
                onClick = { audioPreferences.automaticBackgroundPlayback.set(!automaticBackgroundPlayback) },
                onLongClick = { activity.triggerBackgroundPlayback() },
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
              )
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.background_playback),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = icon,
            onClick = { audioPreferences.automaticBackgroundPlayback.set(!automaticBackgroundPlayback) },
            onLongClick = { activity.triggerBackgroundPlayback() },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.AMBIENT_MODE -> {
        val isAmbientEnabled by viewModel.isAmbientEnabled.collectAsState()
        
        if (isMoreSheet) {
            Surface(
              shape = CircleShape,
              color = if (isAmbientEnabled) activeSurfaceColor else surfaceColor,
              contentColor = if (isAmbientEnabled) activeContentColor else contentColor,
              border = if (isAmbientEnabled) activeBorderColor else borderColor,
              modifier = Modifier
                .height(buttonSize)
                .clip(CircleShape)
                .clickable { viewModel.toggleAmbientMode() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
              ) {
                Icon(
                  imageVector = if (isAmbientEnabled) Icons.Filled.BlurOn else Icons.Outlined.BlurOn,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  text = stringResource(R.string.ambient),
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
              }
            }
        } else {
            @OptIn(ExperimentalFoundationApi::class)
            Surface(
              shape = CircleShape,
              color = if (isAmbientEnabled) activeSurfaceColor else surfaceColor,
              contentColor = if (isAmbientEnabled) activeContentColor else contentColor,
              border = if (isAmbientEnabled) activeBorderColor else borderColor,
              modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = ripple(bounded = true),
                  onClick = { 
                    clickEvent()
                    viewModel.toggleAmbientMode() 
                  }
                ),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isAmbientEnabled) Icons.Filled.BlurOn else Icons.Outlined.BlurOn,
                  contentDescription = stringResource(R.string.ambience_mode),
                  tint = if (isAmbientEnabled) activeContentColor else contentColor,
                  modifier = Modifier.size(24.dp)
                )
              }
            }
        }
    }

    PlayerButton.SLEEP_TIMER -> {
      val remainingTime by viewModel.remainingTime.collectAsState()
      val isActive = remainingTime > 0

      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (isActive) activeSurfaceColor else surfaceColor,
            contentColor = if (isActive) activeContentColor else contentColor,
            border = if (isActive) activeBorderColor else borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable {
                onOpenSheet(Sheets.SleepTimer)
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = if (isActive) {
                  android.text.format.DateUtils.formatElapsedTime(remainingTime.toLong())
                } else {
                  stringResource(R.string.sleep_timer)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
        Surface(
          shape = CircleShape,
          color = if (isActive) activeSurfaceColor else surfaceColor,
          border = if (isActive) activeBorderColor else borderColor,
          modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = { onOpenSheet(Sheets.SleepTimer) }),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.Timer,
              contentDescription = stringResource(R.string.sleep_timer),
              tint = if (isActive) activeContentColor else contentColor,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }
    }

    PlayerButton.NONE -> { /* Do nothing */ }
    PlayerButton.QUALITY -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.Quality) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Outlined.Hd,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = "Quality",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else if (enableExpandingControls) {
          ExpandingQualityButton(
            viewModel = viewModel,
            onOpenSheet = onOpenSheet,
            buttonSize = buttonSize,
            modifier = Modifier
          )
      } else {
          ControlsButton(
            Icons.Outlined.Hd,
            onClick = { onOpenSheet(Sheets.Quality) },
            modifier = Modifier.size(buttonSize),
          )
      }
    }
    PlayerButton.METADATA -> {
      val isCineHubEnabled = org.koin.compose.koinInject<xyz.mpv.rex.preferences.BrowserPreferences>().enableCineHubIntegration.get()
      if (isCineHubEnabled) {
        if (enableExpandingControls && !isMoreSheet) {
          DynamicMediaInfoRectangle(
            viewModel = viewModel,
            onClick = {
              clickEvent()
              viewModel.toggleInfoOnScreenExpanded()
            },
            onLongClick = {
              clickEvent()
              onOpenSheet(Sheets.Metadata)
            },
            isMoreSheet = isMoreSheet,
            buttonSize = buttonSize
          )
        } else if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = surfaceColor,
            contentColor = contentColor,
            border = borderColor,
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.Metadata) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.smaller)
            ) {
              Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.metadata),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
        } else {
          ControlsButton(
            Icons.Default.Info,
            onClick = { onOpenSheet(Sheets.Metadata) },
            modifier = Modifier.size(buttonSize),
          )
        }
      }
    }
  }
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.CircleShape,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val appearancePreferences = org.koin.compose.koinInject<xyz.mpv.rex.preferences.AppearancePreferences>()
    val enableGlass by appearancePreferences.enableGlassPlayerControls.collectAsState()
    val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
    val matchTheme by appearancePreferences.matchPlayerControlsToTheme.collectAsState()

    val activeSurfaceColor = when {
        hideBackground -> androidx.compose.ui.graphics.Color.Transparent
        matchTheme -> androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
    }

    val isActive = color == activeSurfaceColor
    
    val glassModifier = if (enableGlass && color != androidx.compose.ui.graphics.Color.Transparent) {
        Modifier.glassSurface(
            shape = shape as? RoundedCornerShape ?: androidx.compose.foundation.shape.CircleShape,
            backgroundColor = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            borderColor = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
            borderWidth = 1.dp,
            outerShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.00f),
            outerShadowBlur = 0.dp,
            outerShadowOffsetX = 0.dp,
            outerShadowOffsetY = 0.dp,
            innerHighlightColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
            innerHighlightBlur = 5.dp,
            innerHighlightOffsetX = (-2).dp,
            innerHighlightOffsetY = (-2).dp,
            innerShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
            innerShadowBlur = 5.dp,
            innerShadowOffsetX = 2.dp,
            innerShadowOffsetY = 2.dp
        )
    } else {
        Modifier
    }

    val finalColor = if (enableGlass && color != androidx.compose.ui.graphics.Color.Transparent) androidx.compose.ui.graphics.Color.Transparent else color
    val finalBorder = if (enableGlass && color != androidx.compose.ui.graphics.Color.Transparent) null else border

    M3Surface(
        modifier = modifier.then(glassModifier),
        shape = shape,
        color = finalColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = finalBorder,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DynamicMediaInfoRectangle(
  viewModel: PlayerViewModel,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  isMoreSheet: Boolean = false,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 44.dp
) {
  val context = LocalContext.current
  val currentFilePath = `is`.xyz.mpv.MPVLib.getPropertyString("path") ?: ""
  val mediaTitle = viewModel.mediaTitle.collectAsState().value ?: ""

  val localPosterSync = remember(currentFilePath) {
    if (currentFilePath.isNotBlank() && !currentFilePath.startsWith("http") && !currentFilePath.contains("://")) {
      try {
        NfoScanner.findLocalPosterForVideo(java.io.File(currentFilePath))
      } catch (_: Exception) { null }
    } else null
  }

  val customSourceType by viewModel.customMediaSourceType.collectAsState()
  val customPoster by viewModel.customMediaPosterUrl.collectAsState()
  val customOverview by viewModel.customMediaOverview.collectAsState()
  val customYear by viewModel.customMediaYear.collectAsState()
  val customRating by viewModel.customMediaRating.collectAsState()
  val customProvider by viewModel.customMediaProvider.collectAsState()

  var isExpanded by remember { mutableStateOf(false) }
  var activeResolution by remember { mutableStateOf<ActiveMediaResolution?>(null) }

  LaunchedEffect(currentFilePath, mediaTitle, customPoster) {
    if (customPoster.isNullOrBlank()) {
      val clean = xyz.mpv.rex.utils.media.MediaUtils.extractCleanMediaTitle(mediaTitle, currentFilePath)
      if (clean.isNotBlank() || (currentFilePath.isNotBlank() && !currentFilePath.startsWith("http"))) {
        activeResolution = CineOnlineScraper.resolveActiveMedia(
          context = context,
          filePath = currentFilePath,
          mediaTitle = clean
        )
      }
    }
  }

  val effectivePoster: String? = customPoster ?: activeResolution?.posterUrl ?: localPosterSync
  val effectiveTitle: String = mediaTitle.ifBlank {
    when (val res = activeResolution) {
      is ActiveMediaResolution.Movie -> res.movie.title
      is ActiveMediaResolution.TvShow -> res.show.title
      is ActiveMediaResolution.Normal -> res.title
      null -> "Now Playing"
    }
  }
  val effectiveOverview: String = customOverview ?: when (val res = activeResolution) {
    is ActiveMediaResolution.Movie -> res.movie.plot
    is ActiveMediaResolution.TvShow -> res.episodePlot.ifBlank { res.show.plot }
    else -> ""
  }
  val effectiveYear: String = customYear ?: when (val res = activeResolution) {
    is ActiveMediaResolution.Movie -> res.movie.premiered.take(4)
    is ActiveMediaResolution.TvShow -> res.show.premiered.take(4)
    else -> ""
  }
  val effectiveRating: String = customRating ?: when (val res = activeResolution) {
    is ActiveMediaResolution.Movie -> res.movie.userRating.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: ""
    is ActiveMediaResolution.TvShow -> res.show.userRating.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: ""
    else -> ""
  }
  val effectiveProvider: String = customProvider ?: "CineHub"

  if (customSourceType == "cinetv") {
    val shape = RoundedCornerShape(10.dp)
    Surface(
      shape = shape,
      color = Color.Black.copy(alpha = 0.75f),
      border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)),
      shadowElevation = 6.dp,
      modifier = modifier
        .size(buttonSize)
        .clip(shape)
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick
        )
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        if (!effectivePoster.isNullOrBlank()) {
          AsyncImage(
            model = effectivePoster,
            contentDescription = "Channel Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            imageVector = Icons.Outlined.LiveTv,
            contentDescription = "Fallback Logo",
            tint = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }
  } else {
    val posterHeight = buttonSize * 2.5f
    val posterWidth = posterHeight * (2f / 3f)

    Surface(
      shape = RoundedCornerShape(14.dp),
      color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.75f),
      border = BorderStroke(1.5.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.45f)),
      shadowElevation = 8.dp,
      modifier = modifier
        .animateContentSize(
          animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.8f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
          )
        )
        .height(posterHeight)
        .width(if (isExpanded) 330.dp else posterWidth)
        .clip(RoundedCornerShape(14.dp))
        .combinedClickable(
          onClick = {
            isExpanded = !isExpanded
          },
          onLongClick = onLongClick
        )
    ) {
      if (!isExpanded) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          if (!effectivePoster.isNullOrBlank()) {
            AsyncImage(
              model = effectivePoster,
              contentDescription = "Media Poster",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(Color(0xFF2C3246), Color(0xFF141622))
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = when (activeResolution) {
                  is ActiveMediaResolution.TvShow -> Icons.Outlined.Tv
                  else -> Icons.Outlined.Movie
                },
                contentDescription = "Media Poster",
                tint = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(28.dp)
              )
            }
          }
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(width = 54.dp, height = 82.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF1E2130)),
            contentAlignment = Alignment.Center
          ) {
            if (!effectivePoster.isNullOrBlank()) {
              AsyncImage(
                model = effectivePoster,
                contentDescription = effectiveTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
              )
            }
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly
          ) {
            Text(
              text = effectiveTitle,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              if (effectiveYear.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                  Text(
                    text = effectiveYear,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                  )
                }
              }

              if (effectiveRating.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = Color(0xFFFFB800).copy(alpha = 0.2f),
                ) {
                  Text(
                    text = "★ $effectiveRating",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC72C),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                  )
                }
              }

              if (effectiveProvider.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                  Text(
                    text = effectiveProvider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                  )
                }
              }
            }

            if (effectiveOverview.isNotBlank()) {
              Text(
                text = effectiveOverview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                lineHeight = 13.sp
              )
            }
          }

          Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .fillMaxHeight()
              .padding(start = 4.dp)
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable {
                  onLongClick?.invoke()
                }
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = "Metadata Details",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            Surface(
              shape = CircleShape,
              color = Color.Transparent,
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable {
                  isExpanded = false
                }
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Collapse",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandingQualityButton(
  viewModel: PlayerViewModel,
  onOpenSheet: (Sheets) -> Unit,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 44.dp
) {
  val context = LocalContext.current
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isExpanded by remember { mutableStateOf(false) }
  val availableStreamQualities by viewModel.availableStreamQualities.collectAsState()
  val selectedQualityUrl by viewModel.selectedQualityUrl.collectAsState()
  val currentQualityName by viewModel.currentQualityName.collectAsState()

  Surface(
    shape = if (isExpanded) RoundedCornerShape(16.dp) else CircleShape,
    color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    shadowElevation = if (isExpanded) 8.dp else 0.dp,
    modifier = modifier
      .animateContentSize(
        animationSpec = androidx.compose.animation.core.spring(
          dampingRatio = 0.82f,
          stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )
      )
  ) {
    if (!isExpanded) {
      Box(
        modifier = Modifier
          .size(buttonSize)
          .clip(CircleShape)
          .combinedClickable(
            onClick = {
              clickEvent()
              isExpanded = true
            },
            onLongClick = {
              clickEvent()
              onOpenSheet(Sheets.Quality)
            }
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Outlined.Hd,
          contentDescription = "Quality",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(24.dp)
        )
      }
    } else {
      Column(
        modifier = Modifier
          .width(160.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Hd,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Quality",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
          }
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .clickable { isExpanded = false }
          )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          if (availableStreamQualities.isNotEmpty()) {
            availableStreamQualities.forEach { qItem ->
              val label = if (qItem.quality > 0) "${qItem.quality}p" else qItem.name.substringBefore(" -")
              val isSelected = (selectedQualityUrl == qItem.url) || (currentQualityName.equals(label, ignoreCase = true))

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(32.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    viewModel.selectStreamQuality(context, qItem)
                    isExpanded = false
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                  )
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "Selected",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          } else {
            listOf("Auto", "1080p", "720p", "480p", "360p").forEach { fallbackLabel ->
              val isSelected = currentQualityName.equals(fallbackLabel, ignoreCase = true) || (fallbackLabel == "Auto" && (currentQualityName.isBlank() || currentQualityName == "Auto"))

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(32.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    val match = availableStreamQualities.firstOrNull { it.name.contains(fallbackLabel, ignoreCase = true) || "${it.quality}p" == fallbackLabel }
                    if (match != null) {
                      viewModel.selectStreamQuality(context, match)
                    } else {
                      viewModel.selectQualityFormat(context, fallbackLabel)
                    }
                    isExpanded = false
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = fallbackLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                  )
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "Selected",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
              isExpanded = false
              onOpenSheet(Sheets.Quality)
            }
            .padding(horizontal = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "Full Settings",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandingAudioButton(
  viewModel: PlayerViewModel,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 44.dp
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isExpanded by remember { mutableStateOf(false) }
  val audioTracks by viewModel.audioTracks.collectAsState()
  val currentAid by `is`.xyz.mpv.MPVLib.propInt["aid"].collectAsState()
  val activeAid = currentAid

  Surface(
    shape = if (isExpanded) RoundedCornerShape(16.dp) else CircleShape,
    color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    shadowElevation = if (isExpanded) 8.dp else 0.dp,
    modifier = modifier
      .animateContentSize(
        animationSpec = androidx.compose.animation.core.spring(
          dampingRatio = 0.82f,
          stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )
      )
  ) {
    if (!isExpanded) {
      Box(
        modifier = Modifier
          .size(buttonSize)
          .clip(CircleShape)
          .combinedClickable(
            onClick = {
              clickEvent()
              isExpanded = true
            },
            onLongClick = {
              clickEvent()
              onOpenPanel(Panels.AudioDelay)
            }
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Audiotrack,
          contentDescription = "Audio Tracks",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(24.dp)
        )
      }
    } else {
      Column(
        modifier = Modifier
          .width(180.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Audiotrack,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Audio",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
          }
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .clickable { isExpanded = false }
          )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          if (audioTracks.isNotEmpty()) {
            audioTracks.forEach { track ->
              val effectiveAid = activeAid?.takeIf { it > 0 }
                ?: audioTracks.firstOrNull { it.selected == true }?.id
                ?: runCatching { `is`.xyz.mpv.MPVLib.getPropertyInt("aid") }.getOrNull()?.takeIf { it > 0 }
              val isSelected = (effectiveAid != null && track.id == effectiveAid)
              val label = track.title?.takeIf { it.isNotBlank() } ?: track.lang?.uppercase() ?: "Audio Track ${track.id}"

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(32.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    viewModel.selectAudioTrack(track.id, track.title, track.lang)
                    isExpanded = false
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "Selected",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          } else {
            Text(
              text = "Default Audio",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable {
                isExpanded = false
                onOpenPanel(Panels.AudioDelay)
              }
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Delay",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable {
                isExpanded = false
                onOpenSheet(Sheets.AudioTracks)
              }
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "More",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandingSubtitleButton(
  viewModel: PlayerViewModel,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 44.dp
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isExpanded by remember { mutableStateOf(false) }
  val subtitleTracks by viewModel.subtitleTracks.collectAsState()
  val currentSid by `is`.xyz.mpv.MPVLib.propInt["sid"].collectAsState()
  val activeSid = currentSid

  Surface(
    shape = if (isExpanded) RoundedCornerShape(16.dp) else CircleShape,
    color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    shadowElevation = if (isExpanded) 8.dp else 0.dp,
    modifier = modifier
      .animateContentSize(
        animationSpec = androidx.compose.animation.core.spring(
          dampingRatio = 0.82f,
          stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )
      )
  ) {
    if (!isExpanded) {
      Box(
        modifier = Modifier
          .size(buttonSize)
          .clip(CircleShape)
          .combinedClickable(
            onClick = {
              clickEvent()
              isExpanded = true
            },
            onLongClick = {
              clickEvent()
              onOpenPanel(Panels.SubtitleDelay)
            }
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Subtitles,
          contentDescription = "Subtitle Tracks",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(24.dp)
        )
      }
    } else {
      Column(
        modifier = Modifier
          .width(180.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Subtitles,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Subtitles",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
          }
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .clickable { isExpanded = false }
          )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          val isOff = activeSid == 0 || (activeSid == null && subtitleTracks.none { it.selected == true })
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isOff) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isOff) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .fillMaxWidth()
              .height(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable {
                `is`.xyz.mpv.MPVLib.setPropertyInt("sid", 0)
                viewModel.toggleSubtitle(0)
                isExpanded = false
              }
          ) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Off",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isOff) FontWeight.Bold else FontWeight.Normal
              )
              if (isOff) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }

          if (subtitleTracks.isNotEmpty()) {
            subtitleTracks.forEach { track ->
              val isSelected = !isOff && (if (activeSid != null && activeSid > 0) track.id == activeSid else track.selected == true)
              val label = track.title?.takeIf { it.isNotBlank() } ?: track.lang?.uppercase() ?: "Sub ${track.id}"

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(32.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    `is`.xyz.mpv.MPVLib.setPropertyInt("sid", track.id)
                    viewModel.toggleSubtitle(track.id)
                    isExpanded = false
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = "Selected",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable {
                isExpanded = false
                onOpenPanel(Panels.SubtitleDelay)
              }
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Delay",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable {
                isExpanded = false
                onOpenSheet(Sheets.SubtitleTracks)
              }
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Search",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandingChaptersButton(
  chapters: List<dev.vivvvek.seeker.Segment>,
  currentChapter: Int?,
  onOpenSheet: (Sheets) -> Unit,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 44.dp
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isExpanded by remember { mutableStateOf(false) }

  Surface(
    shape = if (isExpanded) RoundedCornerShape(16.dp) else CircleShape,
    color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    shadowElevation = if (isExpanded) 8.dp else 0.dp,
    modifier = modifier
      .animateContentSize(
        animationSpec = androidx.compose.animation.core.spring(
          dampingRatio = 0.82f,
          stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )
      )
  ) {
    if (!isExpanded) {
      Box(
        modifier = Modifier
          .size(buttonSize)
          .clip(CircleShape)
          .combinedClickable(
            onClick = {
              clickEvent()
              isExpanded = true
            },
            onLongClick = {
              clickEvent()
              onOpenSheet(Sheets.Chapters)
            }
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Bookmarks,
          contentDescription = "Chapters",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(24.dp)
        )
      }
    } else {
      Column(
        modifier = Modifier
          .width(200.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Bookmarks,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Chapters",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
          }
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .clickable { isExpanded = false }
          )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          chapters.forEachIndexed { index, chapter ->
            val isSelected = currentChapter == index
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
              contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  `is`.xyz.mpv.MPVLib.setPropertyInt("chapter", index)
                  isExpanded = false
                }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = chapter.name.ifBlank { "Chapter ${index + 1}" },
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  modifier = Modifier.weight(1f, fill = false)
                )
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
              isExpanded = false
              onOpenSheet(Sheets.Chapters)
            }
            .padding(horizontal = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "All Chapters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}


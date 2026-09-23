package xyz.mpv.rex.ui.browser.cinehub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamDubSubBadge
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamMetadataHelper
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamNewBadge
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamQualityBadge
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamRatingBadge
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable
import kotlin.math.absoluteValue

data class CarouselMovie(
  val id: String,
  val title: String,
  val subtitle: String = "",
  val posterUrl: String? = null,
  val backdropUrl: String? = null,
  val rating: Double? = null,
  val year: String? = null,
  val quality: String? = null,
  val dubSub: String? = null,
  val runtime: String? = null,
  val genres: List<String> = emptyList(),
  val isNew: Boolean = false,
  val originalItem: Any
)

/**
 * Flagship Hero Movie Carousel (Part 3).
 * Spans full banner width with high-resolution landscape artwork, multi-stop gradient scrim,
 * dynamic badges for detected Quality, Dub/Sub, Rating, Year, Runtime, Genres, and dual Action Triggers.
 */
@Composable
fun HeroMovieCarousel(
  movies: List<CarouselMovie>,
  onMovieClick: (CarouselMovie) -> Unit,
  onPlayClick: ((CarouselMovie) -> Unit)? = null,
  modifier: Modifier = Modifier,
  pagerState: PagerState = rememberPagerState(
    initialPage = 0,
    pageCount = { movies.size }
  )
) {
  if (movies.isEmpty()) return

  // Auto-pager rotation with paused state when interacting
  LaunchedEffect(pagerState.pageCount) {
    if (movies.size > 1) {
      while (true) {
        delay(6000)
        if (!pagerState.isScrollInProgress) {
          val nextPage = (pagerState.currentPage + 1) % movies.size
          pagerState.animateScrollToPage(nextPage)
        }
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 4.dp, bottom = 12.dp)
  ) {
    HorizontalPager(
      state = pagerState,
      contentPadding = PaddingValues(horizontal = 16.dp),
      pageSpacing = 12.dp,
      beyondViewportPageCount = 2,
      flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = SpringSpec(
          dampingRatio = Spring.DampingRatioLowBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(275.dp)
        .testTag("hero_movie_carousel")
    ) { page ->
      val movie = movies[page]

      // Subtle depth scaling for current active banner
      val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
      val absOffset = pageOffset.absoluteValue
      val scale = 1.0f - (absOffset * 0.04f)

      FullBannerMovieCard(
        movie = movie,
        modifier = Modifier.graphicsLayer {
          scaleX = scale
          scaleY = scale
        },
        onClick = { onMovieClick(movie) },
        onPlayClick = { (onPlayClick ?: onMovieClick)(movie) }
      )
    }

    // Animated pagination indicator pills
    if (movies.size > 1) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val displayCount = movies.size.coerceAtMost(8)
        repeat(displayCount) { index ->
          val isSelected = pagerState.currentPage % displayCount == index
          val dotWidth by animateDpAsState(
            targetValue = if (isSelected) 26.dp else 6.dp,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "banner_dot_width"
          )
          val dotColor = if (isSelected) {
            MaxStreamTheme.CrimsonAccent
          } else {
            Color.White.copy(alpha = 0.25f)
          }

          Box(
            modifier = Modifier
              .padding(horizontal = 3.dp)
              .height(4.dp)
              .width(dotWidth)
              .clip(CircleShape)
              .background(dotColor)
          )
        }
      }
    }
  }
}

/**
 * Flagship Full Banner Movie Card with Landscape Artwork and dynamic metadata overlay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullBannerMovieCard(
  movie: CarouselMovie,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
  onPlayClick: () -> Unit
) {
  val shape = MaxStreamTheme.HeroCardShape
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  Surface(
    shape = shape,
    color = MaxStreamTheme.ElevatedSurface,
    shadowElevation = if (isFocused) 16.dp else 6.dp,
    border = BorderStroke(1.dp, if (isFocused) MaxStreamTheme.CrimsonAccent else Color.White.copy(alpha = 0.10f)),
    modifier = modifier
      .fillMaxWidth()
      .height(275.dp)
      .maxStreamTvFocusable(
        onClick = onClick,
        shape = shape,
        interactionSource = interactionSource
      )
      .clip(shape)
      .clickable(onClick = onClick)
      .testTag("carousel_card_${movie.id}")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Landscape Fanart / Backdrop Image (Prioritize landscape backdrop, fallback to poster)
      val highResBackdrop = MaxStreamMetadataHelper.toHighResFanart(movie.backdropUrl ?: movie.posterUrl)
      AsyncImage(
        model = highResBackdrop,
        contentDescription = movie.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Cinematic Multi-Stop Atmospheric Gradient Scrim
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.35f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.50f),
                Color.Black.copy(alpha = 0.96f)
              ),
              startY = 0f,
              endY = Float.POSITIVE_INFINITY
            )
          )
      )

      // Top Row: Dynamic Badges (Quality, Dub/Sub, NEW, Rating)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (movie.isNew) {
            MaxStreamNewBadge()
          }
          if (!movie.quality.isNullOrBlank()) {
            MaxStreamQualityBadge(quality = movie.quality)
          }
          if (!movie.dubSub.isNullOrBlank()) {
            MaxStreamDubSubBadge(dubSub = movie.dubSub)
          }
        }

        if (movie.rating != null && movie.rating > 0.0) {
          MaxStreamRatingBadge(rating = movie.rating)
        }
      }

      // Bottom Content Overlay: Title, Metadata Strip, Genres & Action Buttons
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Title
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 21.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.2).sp
          ),
          fontWeight = FontWeight.Black,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        // Metadata Strip: Year, Runtime, Subtitle
        val metaParts = buildList {
          if (!movie.year.isNullOrBlank()) add(movie.year)
          if (!movie.runtime.isNullOrBlank()) add(movie.runtime)
          if (movie.subtitle.isNotBlank() && movie.subtitle != movie.year) add(movie.subtitle)
        }

        if (metaParts.isNotEmpty()) {
          Text(
            text = metaParts.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall.copy(
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Genres Pills
        if (movie.genres.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp)
          ) {
            movie.genres.take(3).forEach { genre ->
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f))
              ) {
                Text(
                  text = genre,
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = Color.White.copy(alpha = 0.90f),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
        }

        // Action Buttons: Watch Now & Details
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 4.dp)
        ) {
          Button(
            onClick = onPlayClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaxStreamTheme.CrimsonAccent,
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Watch Now",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
              )
            )
          }

          FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = Color.White.copy(alpha = 0.20f),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Details",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }
      }
    }
  }
}

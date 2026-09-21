package xyz.mpv.rex.ui.browser.cinehub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect
import kotlin.math.absoluteValue

data class CarouselMovie(
  val id: String,
  val title: String,
  val subtitle: String,
  val posterUrl: String?,
  val backdropUrl: String?,
  val rating: Double?,
  val originalItem: Any
)

/**
 * Full Banner Size Hero Movie Carousel.
 * Spans full banner width with rich cinematic backdrop imagery, multi-stop gradient scrim,
 * badges for Rating and Quality, Title, Subtitle, and prominent Watch Now & Details buttons.
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

  // Optional subtle auto-pager rotation when not actively touched
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
        .height(260.dp)
        .testTag("hero_movie_carousel")
    ) { page ->
      val movie = movies[page]

      // Subtle scale for current active banner
      val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
      val absOffset = pageOffset.absoluteValue
      val scale = 1.0f - (absOffset * 0.05f)

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
            targetValue = if (isSelected) 24.dp else 6.dp,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "banner_dot_width"
          )
          val dotColor = if (isSelected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
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
 * Full Banner Movie Card with high-impact visuals and dual action buttons.
 */
@Composable
fun FullBannerMovieCard(
  movie: CarouselMovie,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
  onPlayClick: () -> Unit
) {
  val shape = RoundedCornerShape(20.dp)

  Surface(
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    shadowElevation = 6.dp,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    modifier = modifier
      .fillMaxWidth()
      .height(255.dp)
      .clip(shape)
      .clickable(onClick = onClick)
      .testTag("carousel_card_${movie.id}")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Backdrop / Poster Image
      AsyncImage(
        model = movie.backdropUrl ?: movie.posterUrl,
        contentDescription = movie.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Cinematic multi-stop gradient scrim
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.35f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.55f),
                Color.Black.copy(alpha = 0.95f)
              ),
              startY = 0f,
              endY = Float.POSITIVE_INFINITY
            )
          )
      )

      // Top Row Badges: Quality & Rating
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Quality badge
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        ) {
          Text(
            text = "4K HDR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        // Rating badge
        if (movie.rating != null && movie.rating > 0.0) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .intelligentGlassEffect(
                shape = CircleShape,
                backgroundColor = Color.Black.copy(alpha = 0.55f),
                borderColor = Color.White.copy(alpha = 0.2f)
              )
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = null,
              tint = Color(0xFFFFC107),
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = String.format("%.1f", movie.rating),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }
      }

      // Bottom Content Overlay: Title, Subtitle & Action Buttons
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 24.sp
          ),
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        if (movie.subtitle.isNotBlank()) {
          Text(
            text = movie.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
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
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
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
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
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
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}


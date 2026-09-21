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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * OpenTune-style 3D CoverFlow Hero Movie Carousel.
 * The center card is scaled to 1.0, while side cards are scaled to 0.8 and partially visible.
 * Features smooth swipe, snap-to-center spring animations, rounded corners (24dp),
 * subtle border, and bottom gradient overlay displaying the title and subtitle.
 */
@Composable
fun HeroMovieCarousel(
  movies: List<CarouselMovie>,
  onMovieClick: (CarouselMovie) -> Unit,
  onPlayClick: ((CarouselMovie) -> Unit)? = null,
  modifier: Modifier = Modifier,
  pagerState: PagerState = rememberPagerState(
    initialPage = (movies.size / 2).coerceAtLeast(0),
    pageCount = { movies.size }
  )
) {
  if (movies.isEmpty()) return

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 16.dp)
  ) {
    HorizontalPager(
      state = pagerState,
      contentPadding = PaddingValues(horizontal = 56.dp),
      pageSpacing = 16.dp,
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
        .height(300.dp)
        .testTag("hero_movie_carousel")
    ) { page ->
      val movie = movies[page]

      // Calculate 3D CoverFlow page offset
      val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-2f, 2f)
      val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

      // Center movie card should be larger (scale 1.0), side cards smaller (0.8)
      val scale = 1.0f - (absOffset * 0.20f)
      val alpha = 1.0f - (absOffset * 0.25f)
      val rotationY = -pageOffset * 10f

      MovieCarouselCard(
        movie = movie,
        modifier = Modifier
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            this.rotationY = rotationY
            cameraDistance = 14f * density
          },
        onClick = { onMovieClick(movie) }
      )
    }

    // Spring-animated pagination dots
    if (movies.size > 1) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val displayCount = movies.size.coerceAtMost(8)
        repeat(displayCount) { index ->
          val isSelected = pagerState.currentPage % displayCount == index
          val dotWidth by animateDpAsState(
            targetValue = if (isSelected) 20.dp else 6.dp,
            animationSpec = SpringSpec(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            label = "carousel_dot_width"
          )
          val dotColor = if (isSelected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
          }

          Box(
            modifier = Modifier
              .padding(horizontal = 3.dp)
              .height(5.dp)
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
 * OpenTune-style Movie Carousel Card.
 * 24dp rounded corners, subtle border, bottom gradient overlay,
 * and movie title and subtitle displayed directly on the poster.
 */
@Composable
fun MovieCarouselCard(
  movie: CarouselMovie,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val shape = RoundedCornerShape(24.dp)

  Surface(
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    shadowElevation = 8.dp,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
    modifier = modifier
      .fillMaxWidth()
      .height(290.dp)
      .clip(shape)
      .clickable(onClick = onClick)
      .testTag("carousel_card_${movie.id}")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Movie poster
      AsyncImage(
        model = movie.posterUrl ?: movie.backdropUrl,
        contentDescription = movie.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )

      // Bottom gradient overlay for readability
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.25f),
                Color.Black.copy(alpha = 0.88f)
              ),
              startY = 100f
            )
          )
      )

      // Rating badge at top right
      if (movie.rating != null && movie.rating > 0.0) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(14.dp)
            .intelligentGlassEffect(
              shape = CircleShape,
              backgroundColor = Color.Black.copy(alpha = 0.45f),
              borderColor = Color.White.copy(alpha = 0.2f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
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

      // Title & Subtitle overlay at bottom
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 24.sp
          ),
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        if (movie.subtitle.isNotBlank()) {
          Text(
            text = movie.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

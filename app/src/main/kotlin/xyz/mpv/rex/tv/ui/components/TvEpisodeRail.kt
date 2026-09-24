package xyz.mpv.rex.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.mpv.rex.tv.model.TvStreamItem
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass
import xyz.mpv.rex.ui.theme.maxstream.maxStreamTvFocusable

@Composable
fun TvEpisodeRail(
    title: String = "EPISODES",
    episodes: List<TvStreamItem>,
    selectedEpisodeIndex: Int = 0,
    onEpisodePlay: (TvStreamItem) -> Unit,
    onEpisodePlayOnTv: (TvStreamItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (episodes.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaxStreamTheme.ElectricCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White
                )
            }
            Text(
                text = "${episodes.size} Available",
                style = MaterialTheme.typography.labelMedium,
                color = MaxStreamTheme.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            itemsIndexed(episodes) { index, ep ->
                TvEpisodeCard(
                    episode = ep,
                    isSelected = index == selectedEpisodeIndex,
                    onPlay = { onEpisodePlay(ep) },
                    onPlayOnTv = { onEpisodePlayOnTv(ep) }
                )
            }
        }
    }
}

@Composable
fun TvEpisodeCard(
    episode: TvStreamItem,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onPlayOnTv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val epLabel = if (episode.seasonNumber != null && episode.episodeNumber != null) {
        "S${episode.seasonNumber} • EP ${episode.episodeNumber}"
    } else {
        "EPISODE"
    }

    Box(
        modifier = modifier
            .width(220.dp)
            .height(140.dp)
            .maxStreamGlass(
                shape = RoundedCornerShape(18.dp),
                backgroundColor = if (isSelected) MaxStreamTheme.GlassSurfaceActive else MaxStreamTheme.GlassSurface,
                borderColor = if (isSelected) MaxStreamTheme.ElectricCyan else MaxStreamTheme.GlassBorder,
                borderWidth = if (isSelected) 1.5.dp else 1.dp
            )
            .maxStreamTvFocusable(
                onClick = onPlay,
                focusedScale = 1.06f,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onPlay() }
    ) {
        // Episode Thumbnail / Backdrop
        if (!episode.posterUrl.isNullOrEmpty()) {
            AsyncImage(
                model = episode.posterUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dark gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaxStreamTheme.AbyssBackground.copy(alpha = 0.6f),
                            MaxStreamTheme.AbyssBackground.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Episode Content Info
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row (Badge + Quick TV Cast)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) MaxStreamTheme.ElectricCyan else MaxStreamTheme.GlassSurfaceActive)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = epLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.Black else Color.White
                    )
                }

                IconButton(
                    onClick = onPlayOnTv,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaxStreamTheme.GlassSurfaceActive)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Stream Episode to TV",
                        tint = MaxStreamTheme.ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Bottom Row (Title + Duration + Direct Play Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (episode.formattedDuration.isNotEmpty()) {
                        Text(
                            text = episode.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaxStreamTheme.TextSecondary
                        )
                    }
                }

                // Instant Play Circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaxStreamTheme.CrimsonAccent)
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

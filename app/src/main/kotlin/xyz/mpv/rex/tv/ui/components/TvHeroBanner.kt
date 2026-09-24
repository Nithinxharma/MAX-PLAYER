package xyz.mpv.rex.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
fun TvHeroBanner(
    item: TvStreamItem,
    onPlayLocal: (TvStreamItem) -> Unit,
    onPlayOnTv: (TvStreamItem) -> Unit,
    onDetails: ((TvStreamItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, MaxStreamTheme.GlassBorder, RoundedCornerShape(26.dp))
    ) {
        // Hero Backdrop Image
        if (!item.posterUrl.isNullOrEmpty()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaxStreamTheme.ElevatedSurface,
                                MaxStreamTheme.AbyssBackground
                            )
                        )
                    )
            )
        }

        // Deep Cinematic Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaxStreamTheme.AbyssBackground.copy(alpha = 0.5f),
                            MaxStreamTheme.AbyssBackground.copy(alpha = 0.95f),
                            MaxStreamTheme.AbyssBackground
                        ),
                        startY = 60f
                    )
                )
        )

        // Content & Action Elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Feature badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaxStreamTheme.CrimsonAccent)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (item.episodeNumber != null) "TV SHOW" else "FEATURE MOVIE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }

                if (item.rating != null && item.rating > 0.0) {
                    Box(
                        modifier = Modifier
                            .maxStreamGlass(
                                shape = RoundedCornerShape(6.dp),
                                backgroundColor = MaxStreamTheme.GlassSurfaceActive,
                                borderWidth = 1.dp
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = MaxStreamTheme.AmberGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", item.rating),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                if (item.releaseYear.isNotEmpty()) {
                    Text(
                        text = item.releaseYear,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaxStreamTheme.TextSecondary
                    )
                }

                if (item.formattedDuration.isNotEmpty()) {
                    Text(
                        text = "• ${item.formattedDuration}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaxStreamTheme.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.overview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxStreamTheme.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ACCESSIBLE PRIMARY ACTION BUTTONS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Prominent Play Button
                val playText = if (item.seasonNumber != null && item.episodeNumber != null) {
                    "PLAY S${item.seasonNumber}:E${item.episodeNumber}"
                } else {
                    "PLAY MOVIE"
                }

                Button(
                    onClick = { onPlayLocal(item) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxStreamTheme.CrimsonAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .shadow(16.dp, RoundedCornerShape(14.dp), spotColor = MaxStreamTheme.CrimsonAccent)
                        .maxStreamTvFocusable(
                            onClick = { onPlayLocal(item) },
                            focusedScale = 1.05f
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Play on TV (Direct Hotspot Stream) Button
                OutlinedButton(
                    onClick = { onPlayOnTv(item) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaxStreamTheme.ElectricCyan
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                MaxStreamTheme.ElectricCyan.copy(alpha = 0.8f),
                                MaxStreamTheme.NeonViolet.copy(alpha = 0.5f)
                            )
                        ),
                        width = 1.5.dp
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .maxStreamTvFocusable(
                            onClick = { onPlayOnTv(item) },
                            focusedScale = 1.05f
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Play on TV",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PLAY ON TV",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                if (onDetails != null) {
                    IconButton(
                        onClick = { onDetails(item) },
                        modifier = Modifier
                            .size(48.dp)
                            .maxStreamGlass(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = MaxStreamTheme.GlassSurfaceActive
                            )
                            .maxStreamTvFocusable(
                                onClick = { onDetails(item) },
                                focusedScale = 1.08f
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

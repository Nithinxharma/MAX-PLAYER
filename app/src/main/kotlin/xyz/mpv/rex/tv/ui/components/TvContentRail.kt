package xyz.mpv.rex.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
fun TvContentRail(
    title: String,
    items: List<TvStreamItem>,
    onItemSelect: (TvStreamItem) -> Unit,
    onItemPlayOnTv: (TvStreamItem) -> Unit,
    accentColor: Color = MaxStreamTheme.CrimsonAccent,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
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

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            items(items) { item ->
                TvCard(
                    item = item,
                    onClick = { onItemSelect(item) },
                    onPlayOnTv = { onItemPlayOnTv(item) }
                )
            }
        }
    }
}

@Composable
fun TvCard(
    item: TvStreamItem,
    onClick: () -> Unit,
    onPlayOnTv: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(160.dp)
            .height(230.dp)
            .maxStreamGlass(
                shape = RoundedCornerShape(18.dp),
                backgroundColor = MaxStreamTheme.GlassSurface,
                borderColor = MaxStreamTheme.GlassBorder
            )
            .maxStreamTvFocusable(
                onClick = onClick,
                focusedScale = 1.08f,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
    ) {
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
                    .background(MaxStreamTheme.ElevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaxStreamTheme.TextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MaxStreamTheme.AbyssBackground.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // Top Quick Action (Play on TV)
        IconButton(
            onClick = onPlayOnTv,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaxStreamTheme.AbyssBackground.copy(alpha = 0.75f))
                .border(1.dp, MaxStreamTheme.GlassBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = "Cast to TV",
                tint = MaxStreamTheme.ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
        }

        // Bottom Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotEmpty() || item.formattedDuration.isNotEmpty()) {
                Text(
                    text = item.subtitle.ifEmpty { item.formattedDuration },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaxStreamTheme.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

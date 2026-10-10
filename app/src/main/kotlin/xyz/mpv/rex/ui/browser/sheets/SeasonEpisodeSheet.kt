package xyz.mpv.rex.ui.browser.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.Episode
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonEpisodeSheet(
  isOpen: Boolean,
  showTitle: String,
  seasons: List<Int>,
  episodes: List<Episode>,
  onDismiss: () -> Unit,
  onEpisodeClick: (Episode) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
  var selectedSeason by remember { mutableIntStateOf(seasons.firstOrNull() ?: 1) }

  val filteredEpisodes = remember(selectedSeason, episodes) {
    episodes.filter { it.season == selectedSeason || seasons.size == 1 }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 12.dp)
          .size(width = 36.dp, height = 4.dp)
          .background(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            shape = CircleShape
          )
      )
    },
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
      Text(
        text = showTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaxStreamTheme.TextPrimary
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Season Selection Row
      if (seasons.size > 1) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
          items(seasons) { seasonNum ->
            FilterChip(
              selected = selectedSeason == seasonNum,
              onClick = { selectedSeason = seasonNum },
              label = { Text("Season $seasonNum") },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaxStreamTheme.CrimsonAccent,
                selectedLabelColor = Color.White
              )
            )
          }
        }
      }

      // Episodes List
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
      ) {
        items(filteredEpisodes, key = { "${it.season}_${it.episode}_${it.name}" }) { ep ->
          GlassCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onEpisodeClick(ep) }
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (!ep.posterUrl.isNullOrBlank()) {
                AsyncImage(
                  model = ep.posterUrl,
                  contentDescription = ep.name,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .size(width = 80.dp, height = 55.dp)
                    .clip(RoundedCornerShape(8.dp))
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(width = 80.dp, height = 55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaxStreamTheme.GlassSurface),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaxStreamTheme.CrimsonAccent
                  )
                }
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "E${ep.episode} • ${ep.name ?: "Episode ${ep.episode}"}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaxStreamTheme.TextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (!ep.description.isNullOrBlank()) {
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = ep.description!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxStreamTheme.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              IconButton(onClick = { onEpisodeClick(ep) }) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = "Play Episode",
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

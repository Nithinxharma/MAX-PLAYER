package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.controls.components.intelligentGlassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    viewModel: PlayerViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val qualities by viewModel.availableStreamQualities.collectAsState()
    val currentQualityName by viewModel.currentQualityName.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xF210111A),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x66FFFFFF)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Video Quality & Source",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Select quality or alternate server link",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                }
            }

            if (qualities.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .intelligentGlassEffect(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color.White.copy(alpha = 0.05f),
                            borderColor = Color.White.copy(alpha = 0.12f)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Auto Stream Quality Active",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Streaming dynamically in best quality based on network speed and device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(qualities) { item ->
                        val isSelected = currentQualityName.contains(item.quality.toString()) ||
                                currentQualityName.equals(item.name, ignoreCase = true) ||
                                (currentQualityName == "Auto" && item == qualities.firstOrNull())

                        val displayName = remember(item.name, item.quality, item.url) {
                            xyz.mpv.rex.cinehub.utils.StreamLinkFormatter.formatQualityLanguage(
                                quality = item.quality,
                                rawName = item.name,
                                source = null,
                                url = item.url
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .intelligentGlassEffect(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f),
                                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f)
                                )
                                .clickable {
                                    viewModel.selectStreamQuality(context, item)
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (item.quality >= 1080) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f)
                                    ) {
                                        Text(
                                            text = if (item.quality > 0) "${item.quality}p" else "HD",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.quality >= 1080) Color(0xFF81C784) else Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                        if (item.referer.isNotBlank()) {
                                            Text(
                                                text = "Direct Secured Stream",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.50f)
                                            )
                                        }
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package xyz.mpv.rex.tv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.tv.MaxStreamTvManager
import xyz.mpv.rex.tv.model.TvPlaybackState
import xyz.mpv.rex.tv.ui.components.StyledQrCodeView
import xyz.mpv.rex.tv.ui.components.TvRemoteControlDeck
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.theme.maxstream.maxStreamGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxStreamTvBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeItem by MaxStreamTvManager.activeItem.collectAsState()
    val networkInfo by MaxStreamTvManager.networkInfo.collectAsState()
    val server = MaxStreamTvManager.getServer()
    val fallbackStateFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(TvPlaybackState()) }
    val playbackState by (server?.playbackState ?: fallbackStateFlow).collectAsState()

    var showQrCode by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaxStreamTheme.AbyssBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaxStreamTheme.GlassBorderFocused)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaxStreamTheme.CrimsonAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MAXSTREAM TV STREAM",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (networkInfo?.isHotspot == true) "Hotspot Direct Zero-Lag Streaming" else "Local Wi-Fi Streaming",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaxStreamTheme.ElectricCyan
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaxStreamTheme.GlassSurfaceActive)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // TV Connection Info Glass Card
            val tvWebUrl = networkInfo?.tvWebUrl ?: "http://192.168.43.1:8765/tv"
            val isHotspot = networkInfo?.isHotspot == true

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .maxStreamGlass(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = MaxStreamTheme.GlassSurface,
                        borderColor = if (isHotspot) MaxStreamTheme.ElectricCyan.copy(alpha = 0.5f) else MaxStreamTheme.GlassBorder,
                        borderWidth = 1.5.dp
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isHotspot) Icons.Default.WifiTethering else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isHotspot) MaxStreamTheme.ElectricCyan else MaxStreamTheme.AmberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHotspot) "MOBILE HOTSPOT ACTIVE" else "WI-FI CONNECTED",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (isHotspot) MaxStreamTheme.ElectricCyan else MaxStreamTheme.AmberGold
                            )
                        }

                        IconButton(
                            onClick = { MaxStreamTvManager.refreshNetwork(context) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh IP",
                                tint = MaxStreamTheme.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Open this web address on your TV browser:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaxStreamTheme.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // TV URL Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaxStreamTheme.ElevatedSurface)
                            .border(1.dp, MaxStreamTheme.GlassBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("TV URL", tvWebUrl))
                                Toast.makeText(context, "URL Copied: $tvWebUrl", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tvWebUrl,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy URL",
                                tint = MaxStreamTheme.ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // QR Code & Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showQrCode) {
                            StyledQrCodeView(
                                data = tvWebUrl,
                                size = 120.dp
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { MaxStreamTvManager.openTvWebInBrowser(context) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaxStreamTheme.ElectricCyan,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open Web Player",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            OutlinedButton(
                                onClick = { showQrCode = !showQrCode },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(MaxStreamTheme.GlassBorder, MaxStreamTheme.GlassBorderFocused))
                                ),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showQrCode) "Hide QR Code" else "Show QR Code",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Active Stream Card
            if (activeItem != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .maxStreamGlass(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = MaxStreamTheme.GlassSurfaceActive
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "NOW STREAMING TO TV",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaxStreamTheme.CrimsonAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeItem!!.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeItem!!.subtitle.isNotEmpty()) {
                            Text(
                                text = activeItem!!.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaxStreamTheme.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive TV Remote Control Deck
                TvRemoteControlDeck(
                    playbackState = playbackState,
                    onCommand = { action, payload ->
                        MaxStreamTvManager.sendTvCommand(action, payload)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Zero-Lag Direct HTTP Explainer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚡ Zero-Lag Streaming: Your phone directly streams original video files over your local hotspot without Chromecast re-encoding or cloud delay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaxStreamTheme.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

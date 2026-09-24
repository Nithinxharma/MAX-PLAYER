package xyz.mpv.rex.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

enum class EmptyStateType {
    SEARCH,
    DOWNLOADS,
    HISTORY,
    LIBRARY,
    NO_INTERNET,
    GENERIC
}

/**
 * Reusable Max Stream Empty State Screen/Box.
 */
@Composable
fun MaxStreamEmptyState(
    modifier: Modifier = Modifier,
    type: EmptyStateType = EmptyStateType.GENERIC,
    customTitle: String? = null,
    customMessage: String? = null,
    customIcon: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()

    val (icon, title, message) = when (type) {
        EmptyStateType.SEARCH -> Triple(
            customIcon ?: Icons.Outlined.Search,
            customTitle ?: "No Results Found",
            customMessage ?: "Try searching for a different movie, show, anime or actor."
        )
        EmptyStateType.DOWNLOADS -> Triple(
            customIcon ?: Icons.Outlined.DownloadDone,
            customTitle ?: "No Downloads Yet",
            customMessage ?: "Download movies or episodes to watch offline anywhere."
        )
        EmptyStateType.HISTORY -> Triple(
            customIcon ?: Icons.Outlined.History,
            customTitle ?: "Watch History Empty",
            customMessage ?: "Movies and series you stream will appear here for easy resumption."
        )
        EmptyStateType.LIBRARY -> Triple(
            customIcon ?: Icons.Outlined.Folder,
            customTitle ?: "Your Library is Empty",
            customMessage ?: "Add movies and TV series to your watchlist or favorites."
        )
        EmptyStateType.NO_INTERNET -> Triple(
            customIcon ?: Icons.Outlined.CloudOff,
            customTitle ?: "No Internet Connection",
            customMessage ?: "Please check your network settings and try again."
        )
        EmptyStateType.GENERIC -> Triple(
            customIcon ?: Icons.Outlined.Folder,
            customTitle ?: "Nothing Here Yet",
            customMessage ?: "Content will appear here once available."
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaxStreamTheme.CrimsonAccent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxStreamTheme.CrimsonAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Universal Max Stream Error State Component.
 */
@Composable
fun MaxStreamErrorState(
    modifier: Modifier = Modifier,
    errorMessage: String = "Something went wrong while loading content.",
    onRetry: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f))
                    .border(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaxStreamTheme.CrimsonAccent,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "Connection Error",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaxStreamTheme.CrimsonAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

package xyz.mpv.rex.cinehub.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * CloudStream-compatible WatchType enumeration for tracking media item status.
 */
enum class WatchType(
    val id: Int,
    val label: String,
    val icon: ImageVector,
    val color: Color
) {
    NONE(0, "None", Icons.Default.Visibility, Color(0xFF9E9E9E)),
    WATCHING(1, "Watching", Icons.Default.PlayCircle, Color(0xFF2196F3)),
    COMPLETED(2, "Completed", Icons.Default.CheckCircle, Color(0xFF4CAF50)),
    ON_HOLD(3, "On Hold", Icons.Default.PauseCircle, Color(0xFFFF9800)),
    DROPPED(4, "Dropped", Icons.Default.RemoveCircle, Color(0xFFF44336)),
    PLAN_TO_WATCH(5, "Plan to Watch", Icons.Default.Bookmark, Color(0xFFE91E63));

    companion object {
        fun fromId(id: Int): WatchType {
            return entries.find { it.id == id } ?: NONE
        }

        fun fromName(name: String?): WatchType {
            if (name.isNull_or_blank()) return NONE
            return entries.find { it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true) } ?: NONE
        }
        
        private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
    }
}

package xyz.mpv.rex.ui.browser.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

@Composable
fun DeleteConfirmationDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  @androidx.annotation.PluralsRes itemTypePluralRes: Int,
  itemCount: Int,
  itemNames: List<String> = emptyList(),
) {
  if (!isOpen) return

  val isDark = isSystemInDarkTheme()
  val itemText = pluralStringResource(itemTypePluralRes, itemCount)

  MaxStreamGlassDialog(
    onDismissRequest = onDismiss,
    title = stringResource(R.string.delete_files_title, itemCount, itemText),
    icon = Icons.Outlined.DeleteOutline,
    confirmButton = {
      Button(
        onClick = {
          onConfirm()
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaxStreamTheme.CrimsonAccent,
          contentColor = Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          text = stringResource(R.string.delete),
          fontWeight = FontWeight.Bold,
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          text = stringResource(R.string.generic_cancel),
          fontWeight = FontWeight.Medium,
          color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f))
          .border(1.dp, MaxStreamTheme.CrimsonAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
          .padding(14.dp)
      ) {
        Text(
          text = pluralStringResource(R.plurals.delete_items_warning, itemCount),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = if (isDark) Color.White else MaterialTheme.colorScheme.error,
        )
      }

      if (itemNames.isNotEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) MaxStreamTheme.GlassSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (isDark) MaxStreamTheme.GlassBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(14.dp)
        ) {
          val scrollState = rememberScrollState()

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 180.dp)
              .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            itemNames.forEachIndexed { index, name ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
              ) {
                Text(
                  text = "${index + 1}. ",
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
    }
  }
}

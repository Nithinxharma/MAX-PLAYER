package xyz.mpv.rex.ui.browser.dialogs

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

@Composable
fun RenameDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
  currentName: String,
  @androidx.annotation.StringRes itemTypeRes: Int,
  extension: String? = null,
) {
  if (!isOpen) return

  val isDark = isSystemInDarkTheme()
  val baseName = remember(currentName) {
    mutableStateOf(
      TextFieldValue(
        text = currentName,
        selection = TextRange(currentName.length),
      ),
    )
  }
  val isError = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  val emptyNameErr = stringResource(R.string.name_cannot_be_empty)
  val invalidCharsErr = stringResource(R.string.name_cannot_contain_invalid_chars)

  fun validateAndConfirm() {
    val text = baseName.value.text
    when {
      text.isBlank() -> {
        isError.value = true
        errorMessage.value = emptyNameErr
      }

      text.contains("/") || text.contains("\\") -> {
        isError.value = true
        errorMessage.value = invalidCharsErr
      }

      else -> {
        onConfirm(text + (extension ?: ""))
        onDismiss()
      }
    }
  }

  MaxStreamGlassDialog(
    onDismissRequest = onDismiss,
    title = stringResource(R.string.rename_item, stringResource(itemTypeRes)),
    icon = Icons.Outlined.Edit,
    confirmButton = {
      Button(
        onClick = { validateAndConfirm() },
        enabled = baseName.value.text.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaxStreamTheme.CrimsonAccent,
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          text = stringResource(R.string.rename),
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
      OutlinedTextField(
        value = baseName.value,
        onValueChange = {
          baseName.value = it
          isError.value = false
          errorMessage.value = ""
        },
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester),
        label = { Text(stringResource(R.string.new_name), fontWeight = FontWeight.Medium) },
        singleLine = false,
        maxLines = 4,
        isError = isError.value,
        supportingText = if (isError.value) {
          { Text(errorMessage.value, color = MaterialTheme.colorScheme.error) }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaxStreamTheme.CrimsonAccent,
          focusedLabelColor = MaxStreamTheme.CrimsonAccent,
          unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { validateAndConfirm() }),
        shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

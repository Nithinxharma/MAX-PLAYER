package xyz.mpv.rex.ui.theme.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Liquid Glass Modal Alert Dialog
 */
@Composable
fun LiquidGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val isGlass = isLiquidGlassActive()

    if (!isGlass) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            modifier = modifier,
            properties = properties
        )
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            Box(
                modifier = modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(28.dp),
                        alpha = 0.86f,
                        elevation = 24.dp,
                        borderWidth = 1.2.dp,
                        blurRadius = 32.dp
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (icon != null) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier.padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            icon()
                        }
                    }

                    if (title != null) {
                        ProvideTextStyle(value = MaterialTheme.typography.headlineSmall) {
                            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                title()
                            }
                        }
                    }

                    if (text != null) {
                        ProvideTextStyle(
                            value = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Box(modifier = Modifier.padding(bottom = 24.dp)) {
                                text()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Liquid Glass Modal Bottom Sheet with frosted translucent sheet surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit
) {
    val isGlass = isLiquidGlassActive()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isGlass) Color.White.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        }
    ) {
        if (isGlass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        alpha = 0.84f,
                        elevation = 20.dp,
                        borderWidth = 1.2.dp,
                        blurRadius = 28.dp
                    )
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

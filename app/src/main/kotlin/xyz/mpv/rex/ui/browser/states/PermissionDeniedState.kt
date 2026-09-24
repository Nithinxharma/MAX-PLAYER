@file:Suppress("DEPRECATION")

package xyz.mpv.rex.ui.browser.states

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.utils.permission.PermissionUtils

@SuppressLint("UseKtx")
@Composable
fun PermissionDeniedState(
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val navBarHeight = LocalNavigationBarHeight.current
  var showExplanationDialog by remember { mutableStateOf(false) }

  val isPlayStoreBuild = remember { BuildConfig.SCOPED_STORAGE_ONLY }

  // Animated scale for the icon
  val infiniteTransition = rememberInfiniteTransition(label = "permission_icon")
  val scale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.08f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(2000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "icon_scale",
  )

  val isDark = androidx.compose.foundation.isSystemInDarkTheme()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Scrollable content area (middle)
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        // Animated Icon with Glass styling
        Box(
          modifier = Modifier
            .size(112.dp)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
          text = stringResource(R.string.storage_access_required),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description Glass Card
        xyz.mpv.rex.ui.components.glass.GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = if (isPlayStoreBuild) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  stringResource(R.string.permission_required_photos_videos)
                } else {
                  stringResource(R.string.permission_required_storage)
                }
              } else {
                stringResource(R.string.permission_required_all_files)
              },
              style = MaterialTheme.typography.bodyLarge,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      // Pinned Bottom Section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = navBarHeight + 12.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Allow Access Button
        xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton(
          text = stringResource(R.string.allow_access),
          icon = Icons.Outlined.Warning,
          onClick = {
            PermissionUtils.requestStorageAccess(context, onRequestPermission)
          },
          isPrimary = true,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
          shape = RoundedCornerShape(16.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Why do I see this? link
        TextButton(
          onClick = { showExplanationDialog = true },
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.why_do_i_see_this),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
          )
        }
      }
    }
  }

  // Explanation Dialog in MaxStream Glass
  if (showExplanationDialog) {
    val uriHandler = LocalUriHandler.current
    val githubUrl = "https://github.com/MaxStreamApp/MAX-STREAM"

    xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog(
      onDismissRequest = { showExplanationDialog = false },
      title = stringResource(R.string.why_this_permission_is_needed),
      icon = Icons.Outlined.Info,
      confirmButton = {
        xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton(
          text = stringResource(R.string.got_it),
          onClick = { showExplanationDialog = false },
          isPrimary = true,
        )
      },
      content = {
        Column(
          modifier = Modifier
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (isPlayStoreBuild) {
            Text(
              text = stringResource(R.string.permission_explanation_playstore_intro),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
              text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stringResource(R.string.permission_explanation_tiramisu_plus)
              } else {
                stringResource(R.string.permission_explanation_pre_tiramisu)
              },
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
              text = stringResource(R.string.permission_used_exclusively_for),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
            )

            Text(
              text = stringResource(R.string.permission_usage_bullet_list),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              text = stringResource(R.string.permission_explanation_standard_intro),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
              text = stringResource(R.string.permission_security_policy_change),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
              text = stringResource(R.string.permission_privacy_assurance_standard),
              style = MaterialTheme.typography.bodyMedium,
              color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = stringResource(R.string.opensource_github_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Text(
            text = githubUrl,
            style = MaterialTheme.typography.bodyMedium,
            color = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { uriHandler.openUri(githubUrl) },
          )

          Text(
            text = stringResource(R.string.privacy_assurance_final),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
          )
        }
      },
    )
  }
}

package xyz.mpv.rex.ui.welcome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.BuildConfig
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.locale.LocaleHelper
import xyz.mpv.rex.utils.permission.PermissionUtils

@Serializable
object WelcomeScreen : Screen {

  @Composable
  override fun Content() {
    android.util.Log.d("TRANSITION_TRACE", "WelcomeScreen: first line of Content()")
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExplanationDialog by remember { mutableStateOf(false) }

    var isGranted by remember {
      mutableStateOf(PermissionUtils.isStoragePermissionGranted(context))
    }

    val appIconBitmap = remember(context) {
      try {
        android.util.Log.d("TRANSITION_TRACE", "WelcomeScreen: before packageManager.getApplicationIcon()")
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        android.util.Log.d("TRANSITION_TRACE", "WelcomeScreen: after bitmap creation")
        bitmap.asImageBitmap()
      } catch (e: Exception) {
        android.util.Log.e("TRANSITION_TRACE", "WelcomeScreen: error during icon bitmap creation", e)
        null
      }
    }

    // Re-check permission status when app resumes from system settings dialog
    DisposableEffect(Unit) {
      val lifecycleOwner = context as? LifecycleOwner
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          isGranted = PermissionUtils.isStoragePermissionGranted(context)
        }
      }
      lifecycleOwner?.lifecycle?.addObserver(observer)
      onDispose {
        lifecycleOwner?.lifecycle?.removeObserver(observer)
      }
    }

    fun navigateToMain() {
      appearancePreferences.onboardingCompleted.set(true)
      if (backstack.lastOrNull() == WelcomeScreen) {
        backstack.add(MainScreen)
        backstack.remove(WelcomeScreen)
      }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) {
      isGranted = PermissionUtils.isStoragePermissionGranted(context)
      if (isGranted) {
        navigateToMain()
      }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.AbyssBackground else MaterialTheme.colorScheme.background,
      bottomBar = {
        // Pinned sticky bottom container with Glass styling
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.AbyssBackground.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton(
              text = if (isGranted) {
                stringResource(R.string.welcome_get_started)
              } else {
                stringResource(R.string.welcome_grant_permission)
              },
              icon = if (isGranted) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
              onClick = {
                if (isGranted) {
                  navigateToMain()
                } else {
                  PermissionUtils.requestStorageAccess(context) {
                    permissionLauncher.launch(PermissionUtils.getStoragePermission())
                  }
                }
              },
              isPrimary = true,
              modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
              shape = RoundedCornerShape(16.dp),
            )

            if (!isGranted) {
              Spacer(modifier = Modifier.height(8.dp))
              TextButton(
                onClick = { navigateToMain() },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = stringResource(R.string.welcome_skip_permission),
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      },
    ) { paddingValues ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(
          top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
          bottom = 24.dp,
          start = 24.dp,
          end = 24.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          // App Logo
          Box(
            modifier = Modifier
              .size(88.dp)
              .clip(RoundedCornerShape(24.dp))
              .background(xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.GlassSurface),
            contentAlignment = Alignment.Center
          ) {
            Image(
              painter = painterResource(id = R.drawable.ic_max_stream_mark),
              contentDescription = "MAX STREAM Logo",
              modifier = Modifier.size(76.dp),
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(28.dp))
        }

        item {
          // Language Picker Card
          val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
          xyz.mpv.rex.ui.components.glass.GlassCard(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .clickable { showLanguageDialog = true },
            shape = RoundedCornerShape(20.dp),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.Language,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp),
                  tint = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                )
              }

              Spacer(modifier = Modifier.width(16.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.welcome_language_section_title),
                  style = MaterialTheme.typography.labelMedium,
                  color = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = if (currentLanguage.code.isEmpty()) {
                    stringResource(R.string.system_default)
                  } else {
                    "${currentLanguage.nativeName} (${currentLanguage.localizedName})"
                  },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                )
              }

              TextButton(onClick = { showLanguageDialog = true }) {
                Text(
                  text = stringResource(R.string.browse),
                  fontWeight = FontWeight.SemiBold,
                  color = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
        }

        item {
          // Redesigned Storage Permission Card (Dynamic status & descriptions)
          xyz.mpv.rex.ui.components.glass.GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                      if (isGranted) Color(0xFF22C55E).copy(alpha = 0.2f)
                      else xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent.copy(alpha = 0.15f)
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isGranted) Color(0xFF22C55E) else xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                  )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                  Text(
                    text = if (isGranted) {
                      stringResource(R.string.storage_access_granted)
                    } else {
                      stringResource(R.string.storage_access_required)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = if (isGranted) "All local media is available" else "Required to play downloaded and local videos",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGranted) Color(0xFF22C55E) else xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                    fontWeight = FontWeight.Medium,
                  )
                }
              }

              Text(
                text = if (isGranted) {
                  stringResource(R.string.storage_access_granted_desc)
                } else if (BuildConfig.SCOPED_STORAGE_ONLY) {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stringResource(R.string.permission_required_photos_videos)
                  } else {
                    stringResource(R.string.permission_required_storage)
                  }
                } else {
                  stringResource(R.string.permission_required_all_files)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
              )

              if (!isGranted) {
                // Why is this needed? link
                TextButton(
                  onClick = { showExplanationDialog = true },
                  modifier = Modifier.align(Alignment.CenterHorizontally),
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

          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }

    // Language Selection Dialog in MaxStream Glass
    if (showLanguageDialog) {
      val currentCode = remember { LocaleHelper.getSavedLanguageCode(context) }
      var selectedCode by remember { mutableStateOf(currentCode) }
      val languages = remember { LocaleHelper.getSupportedLanguages(context) }

      xyz.mpv.rex.ui.components.glass.MaxStreamGlassDialog(
        onDismissRequest = { showLanguageDialog = false },
        title = stringResource(R.string.pref_appearance_language_title),
        icon = Icons.Filled.Language,
        confirmButton = {
          xyz.mpv.rex.ui.components.glass.MaxStreamGlassButton(
            text = stringResource(R.string.generic_confirm),
            onClick = {
              LocaleHelper.setAppLanguage(context, selectedCode)
              showLanguageDialog = false
            },
            isPrimary = true,
          )
        },
        dismissButton = {
          TextButton(onClick = { showLanguageDialog = false }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
        content = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 350.dp)
              .verticalScroll(rememberScrollState())
              .selectableGroup(),
          ) {
            languages.forEach { language ->
              val isSelected = selectedCode == language.code
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .selectable(
                    selected = isSelected,
                    onClick = { selectedCode = language.code },
                    role = Role.RadioButton,
                  )
                  .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = null,
                  colors = RadioButtonDefaults.colors(
                    selectedColor = xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.CrimsonAccent,
                  ),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                  )
                  if (language.code.isNotEmpty()) {
                    Text(
                      text = language.localizedName,
                      style = MaterialTheme.typography.bodySmall,
                      color = if (isDark) xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        },
      )
    }

    // Explanation Dialog in MaxStream Glass
    if (showExplanationDialog) {
      val uriHandler = LocalUriHandler.current
      val githubUrl = "https://github.com/MaxStreamApp/MAX-STREAM"
      val isPlayStoreBuild = BuildConfig.SCOPED_STORAGE_ONLY

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
}

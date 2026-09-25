package xyz.mpv.rex.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.components.glass.GlassCard
import xyz.mpv.rex.ui.components.glass.GlassPreferenceItem
import xyz.mpv.rex.ui.components.glass.GlassSettingsSection
import xyz.mpv.rex.ui.components.glass.GlassTopBar
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object SettingsSearchScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusRequester = remember { FocusRequester() }
        val isDark = isSystemInDarkTheme()

        var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(""))
        }

        val searchResults by remember(searchQuery.text) {
            derivedStateOf {
                SearchablePreferences.search(searchQuery.text) { resId ->
                    context.getString(resId)
                }
            }
        }

        // Auto-focus the search field and position cursor at end of text
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            if (searchQuery.text.isNotEmpty()) {
                searchQuery = searchQuery.copy(
                    selection = TextRange(searchQuery.text.length)
                )
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = stringResource(R.string.settings_search_title),
                    onBackClick = backstack::removeLastOrNull
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Glassmorphic Search Bar Container
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.settings_search_hint),
                                color = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaxStreamTheme.CrimsonAccent,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.text.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = TextFieldValue("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Clear",
                                        tint = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboardController?.hide() }
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Results Container
                if (searchQuery.text.isBlank()) {
                    // Glass Hint when query empty
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier.padding(24.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaxStreamTheme.CrimsonAccent.copy(alpha = 0.8f),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.settings_search_hint),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    // Glass No Results Card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier.padding(24.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = if (isDark) MaxStreamTheme.TextMuted else MaterialTheme.colorScheme.outline,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.settings_search_no_results),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = navBarHeight + 24.dp,
                            top = 4.dp
                        ),
                    ) {
                        item {
                            GlassSettingsSection {
                                searchResults.forEachIndexed { index, preference ->
                                    val titleText = if (preference.titleRes != null) {
                                        stringResource(preference.titleRes)
                                    } else {
                                        preference.title ?: ""
                                    }

                                    val summaryText = if (preference.summaryRes != null) {
                                        stringResource(preference.summaryRes)
                                    } else {
                                        preference.summary
                                    }

                                    GlassPreferenceItem(
                                        title = titleText,
                                        subtitle = summaryText,
                                        badge = preference.category,
                                        icon = Icons.Outlined.Settings,
                                        showDivider = index < searchResults.lastIndex,
                                        onClick = {
                                            keyboardController?.hide()
                                            PreferenceHighlightManager.requestHighlight(
                                                key = preference.titleRes ?: preference.title,
                                                targetIndex = preference.targetIndex
                                            )
                                            backstack.add(preference.screen)
                                        }
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

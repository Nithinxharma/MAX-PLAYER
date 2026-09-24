package xyz.mpv.rex.ui.auth

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import xyz.mpv.rex.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import xyz.mpv.rex.auth.AuthManager
import xyz.mpv.rex.auth.model.AuthState
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.auth.components.AnimatedMeshGradient
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.ui.welcome.WelcomeScreen

/**
 * Premium OTT Glassmorphic Login Screen for MAX STREAM.
 */
@Serializable
object LoginScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current
        val scope = rememberCoroutineScope()
        val authManager = koinInject<AuthManager>()
        val appearancePreferences = koinInject<AppearancePreferences>()

        val authState by authManager.authState.collectAsState()
        var isAuthenticating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showEmailDialog by remember { mutableStateOf(false) }

        // Google Sign-In Activity Launcher
        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (data != null) {
                scope.launch {
                    isAuthenticating = true
                    errorMessage = null
                    val syncResult = authManager.handleGoogleSignInResult(data)
                    isAuthenticating = false
                    syncResult.onSuccess {
                        appearancePreferences.onboardingCompleted.set(true)
                        backstack.clear()
                        backstack.add(MainScreen)
                    }.onFailure { error ->
                        val msg = error.localizedMessage ?: "Google sign-in failed. Please try again."
                        if (!msg.contains("cancelled", ignoreCase = true)) {
                            errorMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                isAuthenticating = false
                Log.w("LoginScreen", "Google Sign-In returned null result data (resultCode: ${result.resultCode})")
            }
        }

        // Automatic redirection if authenticated
        LaunchedEffect(authState) {
            if (authState is AuthState.Authenticated || authManager.currentUser != null) {
                appearancePreferences.onboardingCompleted.set(true)
                backstack.clear()
                backstack.add(MainScreen)
            }
        }

        AnimatedMeshGradient {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Premium Max Stream Brand Emblem (No square, no play button, original vector logo)
                    MaxStreamBrandLogo()

                    Spacer(modifier = Modifier.height(28.dp))

                    // Floating Glassmorphic Authentication Card
                    FloatingGlassAuthCard(
                        isLoading = isAuthenticating,
                        errorMessage = errorMessage,
                        onGoogleSignInClick = {
                            if (!isAuthenticating) {
                                isAuthenticating = true
                                errorMessage = null
                                val client = authManager.getGoogleSignInClient(context)
                                client.signOut().addOnCompleteListener {
                                    googleSignInLauncher.launch(client.signInIntent)
                                }
                            }
                        },
                        onEmailSignInClick = {
                            if (!isAuthenticating) {
                                showEmailDialog = true
                            }
                        },
                        onGuestClick = {
                            if (!isAuthenticating) {
                                val hasCompletedOnboarding = appearancePreferences.onboardingCompleted.get()
                                backstack.clear()
                                backstack.add(if (hasCompletedOnboarding) MainScreen else WelcomeScreen)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Email Sign In Dialog
                if (showEmailDialog) {
                    EmailAuthDialog(
                        onDismiss = { showEmailDialog = false },
                        onSuccess = {
                            showEmailDialog = false
                            appearancePreferences.onboardingCompleted.set(true)
                            backstack.clear()
                            backstack.add(MainScreen)
                        },
                        authManager = authManager
                    )
                }
            }
        }
    }
}

/**
 * Premium Max Stream Logo Presentation (Clean, vector, preserved proportions, no square, no play button)
 */
@Composable
private fun MaxStreamBrandLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_max_stream_mark),
            contentDescription = "MAX STREAM",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(96.dp)
                .scale(pulseScale)
        )
    }
}

/**
 * Floating Glass Card (28dp corner radius, 20dp glass blur, 1dp white 15% border, soft shadow)
 */
@Composable
private fun FloatingGlassAuthCard(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleSignInClick: () -> Unit,
    onEmailSignInClick: () -> Unit,
    onGuestClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)

        Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.8f),
                spotColor = Color(0xFF6B21A8).copy(alpha = 0.20f)
            ),
        shape = cardShape,
        color = Color(0xFF0E0E16).copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title Typography
            Text(
                text = "Welcome to Max Stream",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Continue Watching Anywhere",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error banner if any
            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF8B0000).copy(alpha = 0.45f))
                        .border(1.dp, Color(0xFFFF4D4D).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFD1D1),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                // Premium Loading Animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF5F1F),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Authenticating with Firebase...",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // 1. Continue with Google (Official Google 4-color G-mark)
                GlassPillButton(
                    text = "Continue with Google",
                    iconContent = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    highlightBrush = Brush.horizontalGradient(
                        listOf(Color(0xFF6B21A8).copy(alpha = 0.28f), Color(0xFF831843).copy(alpha = 0.22f))
                    ),
                    isPrimary = true,
                    onClick = onGoogleSignInClick,
                    modifier = Modifier.testTag("btn_google_signin")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Continue with Email
                GlassPillButton(
                    text = "Continue with Email",
                    icon = Icons.Default.Email,
                    highlightBrush = null,
                    isPrimary = false,
                    onClick = onEmailSignInClick,
                    modifier = Modifier.testTag("btn_email_signin")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Continue as Guest
                GlassPillButton(
                    text = "Continue as Guest",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    highlightBrush = null,
                    isPrimary = false,
                    isSubtle = true,
                    onClick = onGuestClick,
                    modifier = Modifier.testTag("btn_guest_signin")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "By continuing, you agree to Max Stream's Terms & Privacy Policy.",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Interactive Large Glass Pill Button with 100% -> 103% scale interaction
 */
@Composable
private fun GlassPillButton(
    text: String,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    highlightBrush: Brush? = null,
    isPrimary: Boolean = false,
    isSubtle: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "btn_scale"
    )

    val shape = RoundedCornerShape(50)

    val backgroundColor = when {
        isPrimary -> Color(0xFF161622).copy(alpha = 0.85f)
        isSubtle -> Color.White.copy(alpha = 0.05f)
        else -> Color(0xFF12121A).copy(alpha = 0.65f)
    }

    val borderColor = when {
        isPrimary -> Color.White.copy(alpha = 0.24f)
        isSubtle -> Color.White.copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.16f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Subtle ambient gradient glow for primary button
            if (highlightBrush != null && isPrimary) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.25f)
                        .background(highlightBrush)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                if (iconContent != null) {
                    iconContent()
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSubtle) Color.White.copy(alpha = 0.6f) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = text,
                    color = if (isSubtle) Color.White.copy(alpha = 0.8f) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Modal Dialog for Email/Password Sign-In & Registration
 */
@Composable
private fun EmailAuthDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    authManager: AuthManager
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14141E).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRegisterMode) "Create Account" else "Sign In with Email",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (localError != null) {
                    Text(
                        text = localError!!,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(0.7f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF5F1F),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.White.copy(0.7f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5F1F),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { /* Submit */ }),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(0.7f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5F1F),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFFFF5F1F), modifier = Modifier.size(36.dp))
                } else {
                    GlassPillButton(
                        text = if (isRegisterMode) "Register" else "Sign In",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        highlightBrush = Brush.horizontalGradient(listOf(Color(0xFFFF5F1F), Color(0xFFFF2D55))),
                        isPrimary = true,
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                localError = "Please fill in all fields"
                                return@GlassPillButton
                            }
                            isLoading = true
                            localError = null
                            scope.launch {
                                try {
                                    val authResult = if (isRegisterMode) {
                                        auth.createUserWithEmailAndPassword(email.trim(), password).await()
                                    } else {
                                        auth.signInWithEmailAndPassword(email.trim(), password).await()
                                    }
                                    val user = authResult.user
                                    if (user != null) {
                                        authManager.syncUserToFirestore(user)
                                        onSuccess()
                                    } else {
                                        localError = "Authentication returned empty profile"
                                    }
                                } catch (e: Exception) {
                                    localError = e.localizedMessage ?: "Authentication failed"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRegisterMode) "Already have an account? Sign In" else "New to Max Stream? Create Account",
                        color = Color(0xFFD8B4FE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            isRegisterMode = !isRegisterMode
                            localError = null
                        }
                    )
                }
            }
        }
    }
}

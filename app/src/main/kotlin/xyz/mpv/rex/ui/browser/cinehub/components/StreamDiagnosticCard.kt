package xyz.mpv.rex.ui.browser.cinehub.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * Detailed diagnostic data explaining why a stream extraction or play attempt failed.
 */
data class StreamExtractionDiagnostic(
    val providerName: String,
    val targetUrl: String,
    val isSuccess: Boolean = false,
    val failureReason: String,
    val errorDetails: String,
    val suggestion: String,
    val httpCode: Int? = null,
    val rawException: Throwable? = null,
    val totalExtractorsAttempted: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Diagnoses the exact root cause of a stream extraction failure based on exceptions,
 * HTTP status codes, Cloudflare anti-bot blocks, DNS errors, or empty stream links.
 */
fun diagnoseStreamFailure(
    providerName: String,
    targetUrl: String,
    exception: Throwable?,
    linksCount: Int,
    extractorsCount: Int = 0
): StreamExtractionDiagnostic {
    val message = exception?.message ?: ""
    val exClass = exception?.javaClass?.simpleName ?: ""
    val cause = exception?.cause?.message ?: ""
    val fullText = "$message $cause $exClass"

    return when {
        exception is java.net.UnknownHostException || fullText.contains("Unable to resolve host", ignoreCase = true) || fullText.contains("No address associated with hostname", ignoreCase = true) -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "DNS Resolution Failed (ISP Block / Host Unreachable)",
                errorDetails = "Cannot find IP for domain (${exception?.message ?: targetUrl}).",
                suggestion = "Indian ISPs (Jio/Airtel/Vi) often block movie provider domains at the DNS level. Enable DNS-over-HTTPS (1.1.1.1) or use a VPN.",
                httpCode = null,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        fullText.contains("403") || fullText.contains("Cloudflare", ignoreCase = true) || fullText.contains("Turnstile", ignoreCase = true) || fullText.contains("Just a moment", ignoreCase = true) || fullText.contains("cf_clearance", ignoreCase = true) -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "Cloudflare Anti-Bot Protection (HTTP 403 Forbidden)",
                errorDetails = "The streaming provider website is protected by Cloudflare DDoS / Turnstile challenge.",
                suggestion = "This website is blocking direct HTTP requests. A WebView cookie resolver or Cloudflare clearance token is required.",
                httpCode = 403,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        exception is java.net.SocketTimeoutException || fullText.contains("timeout", ignoreCase = true) -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "Connection Timed Out",
                errorDetails = "Source server did not respond in time (${message.ifBlank { "SocketTimeout" }}).",
                suggestion = "The provider's website server is slow or under heavy traffic. Please retry after a few moments.",
                httpCode = 504,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        exception is java.net.ConnectException || fullText.contains("Connection refused", ignoreCase = true) -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "Server Down / Connection Refused",
                errorDetails = "Unable to connect to source server (${targetUrl.take(60)}).",
                suggestion = "The streaming server is offline, down for maintenance, or its domain has changed.",
                httpCode = 502,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        exception is javax.net.ssl.SSLException || fullText.contains("SSL", ignoreCase = true) || fullText.contains("CertPathValidatorException", ignoreCase = true) -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "SSL / TLS Handshake Failure",
                errorDetails = "Secure SSL handshake failed with provider host (${exception?.message ?: "SSL Error"}).",
                suggestion = "The provider site has an expired SSL certificate or ISP MITM proxy.",
                httpCode = 495,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        fullText.contains("404") || fullText.contains("Not Found", ignoreCase = true) -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "Media Link Not Found (HTTP 404)",
                errorDetails = "The requested media URL no longer exists on provider server.",
                suggestion = "The post or video link was removed or replaced with an updated URL by the uploader.",
                httpCode = 404,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        exception != null -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "Provider Extraction Error (${exClass.ifBlank { "Exception" }})",
                errorDetails = message.ifBlank { "Error while parsing provider page or executing extractor scripts: $exClass" },
                suggestion = "An exception occurred inside the extension's loadLinks logic. Check debug logs for details.",
                httpCode = null,
                rawException = exception,
                totalExtractorsAttempted = extractorsCount
            )
        }
        linksCount == 0 -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = false,
                failureReason = "No Playable Stream Links Found (0 Links)",
                errorDetails = "The media details were loaded, but all video host extractors (HubCloud, Streamwish, Filemoon, Dood, VidCloud, FastDL, etc.) returned 0 stream links.",
                suggestion = "The third-party video hosts linked in this post have expired or are requesting interactive verification.",
                httpCode = 200,
                rawException = null,
                totalExtractorsAttempted = extractorsCount
            )
        }
        else -> {
            StreamExtractionDiagnostic(
                providerName = providerName,
                targetUrl = targetUrl,
                isSuccess = true,
                failureReason = "",
                errorDetails = "Successfully resolved $linksCount stream link(s).",
                suggestion = "",
                httpCode = 200,
                totalExtractorsAttempted = extractorsCount
            )
        }
    }
}

/**
 * Modern Liquid Glass Diagnostic Card that clearly shows the exact root cause
 * of why a stream link failed to load, along with actionable advice, retry,
 * and copy error button.
 */
@Composable
fun StreamDiagnosticCard(
    diagnostic: StreamExtractionDiagnostic,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showRawDetails by remember { mutableStateOf(false) }

    val cardBg = if (isDark) {
        Color(0xFF1E1014).copy(alpha = 0.90f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
    }

    val cardBorder = if (isDark) {
        Color(0xFFFF5252).copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    }

    val accentColor = if (isDark) Color(0xFFFF5252) else MaterialTheme.colorScheme.error
    val textColorPrimary = if (isDark) MaxStreamTheme.TextPrimary else MaterialTheme.colorScheme.onErrorContainer
    val textColorSecondary = if (isDark) MaxStreamTheme.TextSecondary else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.80f)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Icon + Failure Reason + Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.20f))
                            .border(1.dp, accentColor.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Stream Error",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Stream Link Failed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = accentColor
                        )
                        Text(
                            text = diagnostic.failureReason,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = textColorPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = textColorSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Error description
            if (diagnostic.errorDetails.isNotBlank()) {
                Text(
                    text = diagnostic.errorDetails,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = textColorSecondary
                )
            }

            // Solution suggestion
            if (diagnostic.suggestion.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color.Black.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = diagnostic.suggestion,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = textColorPrimary
                        )
                    }
                }
            }

            // Metadata row: Provider + Target URL + Toggle Technical Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "Provider: ${diagnostic.providerName.ifBlank { "Unknown" }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColorPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = if (showRawDetails) "Hide Technical Log ▲" else "View Technical Log ▼",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) MaxStreamTheme.ElectricCyan else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Transparent)
                        .clickable { showRawDetails = !showRawDetails }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Raw technical details expandable box
            AnimatedVisibility(
                visible = showRawDetails,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                val fullTechnicalLog = buildString {
                    appendLine("Provider: ${diagnostic.providerName}")
                    appendLine("Target URL: ${diagnostic.targetUrl}")
                    if (diagnostic.httpCode != null) appendLine("HTTP Status: ${diagnostic.httpCode}")
                    appendLine("Failure: ${diagnostic.failureReason}")
                    appendLine("Details: ${diagnostic.errorDetails}")
                    if (diagnostic.rawException != null) {
                        appendLine("Exception: ${diagnostic.rawException.javaClass.name}: ${diagnostic.rawException.message}")
                        diagnostic.rawException.stackTrace.take(4).forEach { st ->
                            appendLine("  at $st")
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = fullTechnicalLog.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = Color(0xFFEEEEEE)
                    )

                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Stream Diagnostic Log", fullTechnicalLog))
                            Toast.makeText(context, "Copied error log to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Error Log", fontSize = 10.sp)
                    }
                }
            }

            // Action Buttons: Retry + Copy Log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Retry Stream",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val report = buildString {
                            appendLine("--- MAXSTREAM STREAM DIAGNOSTIC ---")
                            appendLine("Provider: ${diagnostic.providerName}")
                            appendLine("Target URL: ${diagnostic.targetUrl}")
                            appendLine("Reason: ${diagnostic.failureReason}")
                            appendLine("Details: ${diagnostic.errorDetails}")
                            appendLine("Suggestion: ${diagnostic.suggestion}")
                            if (diagnostic.rawException != null) {
                                appendLine("Exception: ${diagnostic.rawException.message}")
                            }
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Stream Error", report))
                        Toast.makeText(context, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Error",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Reason", fontSize = 11.sp)
                }
            }
        }
    }
}

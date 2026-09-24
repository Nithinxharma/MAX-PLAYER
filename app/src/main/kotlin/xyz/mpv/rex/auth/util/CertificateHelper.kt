package xyz.mpv.rex.auth.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Utility to extract the active signing certificate SHA-1 and SHA-256 fingerprints
 * from the running application package at runtime.
 */
object CertificateHelper {

    private const val TAG = "CertificateHelper"

    data class CertFingerprints(
        val packageName: String,
        val sha1: String,
        val sha256: String
    )

    /**
     * Inspects the running APK package signatures and returns formatted SHA-1 & SHA-256 fingerprints.
     */
    fun getCertificateFingerprints(context: Context): CertFingerprints {
        val packageName = context.packageName
        try {
            val packageManager = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray()
            if (certBytes != null) {
                val sha1 = computeHash(certBytes, "SHA-1")
                val sha256 = computeHash(certBytes, "SHA-256")
                Log.d(TAG, "[$packageName] Active Cert SHA-1: $sha1")
                Log.d(TAG, "[$packageName] Active Cert SHA-256: $sha256")
                return CertFingerprints(packageName, sha1, sha256)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect package certificate fingerprints: ${e.message}", e)
        }
        return CertFingerprints(packageName, "Unavailable", "Unavailable")
    }

    private fun computeHash(bytes: ByteArray, algorithm: String): String {
        return try {
            val md = MessageDigest.getInstance(algorithm)
            val digest = md.digest(bytes)
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

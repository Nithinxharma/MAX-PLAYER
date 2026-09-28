package com.lagradost.cloudstream3.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * CloudStream upstream CryptoJS Compatible AES Decryption & Encryption Helper.
 * Supports:
 * - OpenSSL / CryptoJS "Salted__" format
 * - AES-128 / AES-256 CBC with EVP KDF (MD5 key expansion)
 * - AES ECB decryption
 * - Base64 encoding and decoding
 */
@Suppress("unused", "FunctionName", "SameParameterValue")
object CryptoJSHelper {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128
    private const val APPEND = "Salted__"

    /**
     * Decrypts a CryptoJS-encrypted base64 string using a passphrase.
     */
    @JvmStatic
    fun decrypt(password: String, cipherText: String): String {
        val ctBytes = try {
            Base64.decode(cipherText, Base64.DEFAULT)
        } catch (e: Throwable) {
            cipherText.toByteArray(Charsets.UTF_8)
        }
        if (ctBytes.size < 16) return ""

        val saltBytes = ctBytes.copyOfRange(8, 16)
        val cipherTextBytes = ctBytes.copyOfRange(16, ctBytes.size)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        evpkdf(password.toByteArray(Charsets.UTF_8), KEY_SIZE, IV_SIZE, saltBytes, 1, key, iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val plainBytes = cipher.doFinal(cipherTextBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Encrypts plaintext using CryptoJS compatible OpenSSL format.
     */
    @JvmStatic
    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        evpkdf(password.toByteArray(Charsets.UTF_8), KEY_SIZE, IV_SIZE, saltBytes, 1, key, iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val sBytes = APPEND.toByteArray(Charsets.UTF_8)
        val b = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        sBytes.copyInto(b, 0)
        saltBytes.copyInto(b, sBytes.size)
        cipherText.copyInto(b, sBytes.size + saltBytes.size)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    /**
     * AES CBC decryption with direct key and IV bytes.
     */
    @JvmStatic
    fun decryptAesCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /**
     * AES ECB decryption with direct key bytes.
     */
    @JvmStatic
    fun decryptAesEcb(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    /**
     * EVP KDF key derivation function matching OpenSSL / CryptoJS implementation.
     */
    @JvmStatic
    fun evpkdf(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        iterations: Int,
        resultKey: ByteArray,
        resultIv: ByteArray,
    ) {
        val keyBytesCount = keySize / 8
        val ivBytesCount = ivSize / 8
        val targetKeySize = keyBytesCount + ivBytesCount
        val derivedBytes = ByteArray(targetKeySize + 16)
        var numberOfDerivedBytes = 0
        var block: ByteArray? = null
        val md5 = MessageDigest.getInstance("MD5")

        while (numberOfDerivedBytes < targetKeySize) {
            md5.reset()
            val currentBlock = block
            if (currentBlock != null) md5.update(currentBlock)
            md5.update(password)
            md5.update(salt)
            var currentDigest = md5.digest()

            for (i in 1 until iterations) {
                md5.reset()
                md5.update(currentDigest)
                currentDigest = md5.digest()
            }

            block = currentDigest
            val copyLength = min(currentDigest.size, targetKeySize - numberOfDerivedBytes)
            currentDigest.copyInto(derivedBytes, numberOfDerivedBytes, 0, copyLength)
            numberOfDerivedBytes += copyLength
        }

        derivedBytes.copyInto(resultKey, 0, 0, keyBytesCount)
        derivedBytes.copyInto(resultIv, 0, keyBytesCount, keyBytesCount + ivBytesCount)
    }

    private fun generateSalt(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}

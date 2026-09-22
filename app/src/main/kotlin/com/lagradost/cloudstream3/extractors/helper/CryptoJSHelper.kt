package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

@Suppress("unused", "FunctionName", "SameParameterValue")
object CryptoJS {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128
    private const val APPEND = "Salted__"

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
        return base64Encode(b)
    }

    fun decrypt(password: String, cipherText: String): String {
        val ctBytes = base64DecodeArray(cipherText)
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

    private fun evpkdf(
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
            if (block != null) md5.update(block)
            md5.update(password)
            md5.update(salt)
            block = md5.digest()

            for (i in 1 until iterations) {
                md5.reset()
                md5.update(block)
                block = md5.digest()
            }

            val copyLength = min(block.size, targetKeySize - numberOfDerivedBytes)
            block.copyInto(derivedBytes, numberOfDerivedBytes, 0, copyLength)
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

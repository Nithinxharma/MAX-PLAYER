package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesHelper {
    suspend fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: Boolean = true,
    ): String? {
        val parse = AppUtils.tryParseJson<AesData>(data) ?: return null
        val (key, iv) = generateKeyAndIv(
            password = pass,
            salt = parse.s.hexToByteArray(),
            ivLength = parse.iv.length / 2,
            saltLength = parse.s.length / 2,
        ) ?: return null

        val cipher = Cipher.getInstance(if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        return try {
            if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val plainBytes = cipher.doFinal(base64DecodeArray(parse.ct))
                String(plainBytes, Charsets.UTF_8)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                val cipherBytes = cipher.doFinal(parse.ct.toByteArray(Charsets.UTF_8))
                base64Encode(cipherBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    @Deprecated(
        message = "Set padding = false for no padding",
        level = DeprecationLevel.WARNING,
    )
    fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: String,
    ): String? {
        val hasPadding = !padding.endsWith("NoPadding")
        val parse = AppUtils.tryParseJson<AesData>(data) ?: return null
        val (key, iv) = generateKeyAndIv(
            password = pass,
            salt = parse.s.hexToByteArray(),
            ivLength = parse.iv.length / 2,
            saltLength = parse.s.length / 2,
        ) ?: return null

        val cipher = Cipher.getInstance(if (hasPadding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        return try {
            if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val plainBytes = cipher.doFinal(base64DecodeArray(parse.ct))
                String(plainBytes, Charsets.UTF_8)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                val cipherBytes = cipher.doFinal(parse.ct.toByteArray(Charsets.UTF_8))
                base64Encode(cipherBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun generateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        keyLength: Int = 32,
        ivLength: Int,
        saltLength: Int,
        iterations: Int = 1,
    ): Pair<ByteArray, ByteArray>? {
        return try {
            val digestLength = 16
            val targetKeySize = keyLength + ivLength
            val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
            val generatedData = ByteArray(requiredLength)
            var generatedLength = 0
            val md5 = MessageDigest.getInstance("MD5")

            while (generatedLength < targetKeySize) {
                md5.reset()
                if (generatedLength > 0) {
                    md5.update(generatedData, generatedLength - digestLength, digestLength)
                }
                md5.update(password)
                md5.update(salt, 0, saltLength)
                var digest = md5.digest()

                for (i in 1 until iterations) {
                    md5.reset()
                    md5.update(digest)
                    digest = md5.digest()
                }

                digest.copyInto(generatedData, generatedLength)
                generatedLength += digestLength
            }

            generatedData.copyOfRange(0, keyLength) to
                    generatedData.copyOfRange(keyLength, targetKeySize)
        } catch (_: Exception) {
            null
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    @Serializable
    private data class AesData(
        @JsonProperty("ct") @SerialName("ct") val ct: String,
        @JsonProperty("iv") @SerialName("iv") val iv: String,
        @JsonProperty("s") @SerialName("s") val s: String,
    )
}

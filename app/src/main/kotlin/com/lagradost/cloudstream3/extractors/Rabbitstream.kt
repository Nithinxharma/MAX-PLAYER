package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Megacloud : Rabbitstream() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.tv"
    override val embed = "embed-2/ajax/e-1"
    private val scriptUrl = "$mainUrl/js/player/a/prod/e1-player.min.js"

    override suspend fun extractRealKey(sources: String): Pair<String, String> {
        val rawKeys = getKeys()
        val sourcesArray = sources.toCharArray()
        var extractedKey = ""
        var currentIndex = 0
        for (index in rawKeys) {
            val start = index[0] + currentIndex
            val end = start + index[1]
            for (i in start until end) {
                if (i in sourcesArray.indices) {
                    extractedKey += sourcesArray[i].toString()
                    sourcesArray[i] = ' '
                }
            }
            currentIndex += index[1]
        }
        return extractedKey to sourcesArray.joinToString("").replace(" ", "")
    }

    private suspend fun getKeys(): List<List<Int>> {
        val script = app.get(scriptUrl).text
        fun matchingKey(value: String): String {
            return Regex(",$value=((?:0x)?([0-9a-fA-F]+))").find(script)?.groupValues?.get(1)
                ?.removePrefix("0x") ?: throw ErrorLoadingException("Failed to match the key")
        }
        val regex = Regex("case\\s*0x[0-9a-f]+:(?![^;]*=partKey)\\s*\\w+\\s*=\\s*(\\w+)\\s*,\\s*\\w+\\s*=\\s*(\\w+);")
        val indexPairs = regex.findAll(script).toList().map { match ->
            val matchKey1 = matchingKey(match.groupValues[1])
            val matchKey2 = matchingKey(match.groupValues[2])
            try {
                listOf(matchKey1.toInt(16), matchKey2.toInt(16))
            } catch (_: NumberFormatException) {
                emptyList()
            }
        }.filter { it.isNotEmpty() }
        return indexPairs
    }
}

class Dokicloud : Rabbitstream() {
    override val name = "Dokicloud"
    override val mainUrl = "https://dokicloud.one"
}

class RapidCloud : Rabbitstream() {
    override val name = "RapidCloud"
    override val mainUrl = "https://rapid-cloud.co"
}

open class Rabbitstream : ExtractorApi() {
    override val name = "Rabbitstream"
    override val mainUrl = "https://rabbitstream.net"
    override val requiresReferer = false
    open val embed = "ajax/embed-4"
    open val key = "https://raw.githubusercontent.com/eatmynerds/key/e4/key.txt"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringAfterLast("/").substringBefore("?")
        val response = app.get(
            "$mainUrl/$embed/getSources?id=$id",
            referer = mainUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
        )
        val encryptedMap = response.parsedSafe<SourcesEncrypted>()
        val sources = encryptedMap?.sources
        val decryptedSources = if (sources == null || encryptedMap.encrypted == false) {
            response.parsedSafe<SourcesResponses>()
        } else {
            val (key, encData) = extractRealKey(sources)
            val decrypted = tryParseJson<List<Sources>>(decrypt(encData, key))
            SourcesResponses(
                sources = decrypted,
                tracks = encryptedMap.tracks,
            )
        }

        decryptedSources?.sources?.forEach { source ->
            val file = source?.file ?: return@forEach
            M3u8Helper.generateM3u8(
                name,
                file,
                "$mainUrl/",
            ).forEach(callback)
        }

        decryptedSources?.tracks?.forEach { track ->
            val file = track?.file ?: return@forEach
            val label = track.label ?: return@forEach
            subtitleCallback(
                newSubtitleFile(
                    label,
                    file,
                )
            )
        }
    }

    open suspend fun extractRealKey(sources: String): Pair<String, String> {
        val rawKeys = parseJson<List<Int>>(app.get(key).text)
        val extractedKey = base64Encode(rawKeys.map { it.toByte() }.toByteArray())
        return extractedKey to sources
    }

    private fun decrypt(input: String, key: String): String {
        return decryptSourceUrl(
            generateKey(
                salt = base64DecodeArray(input).copyOfRange(8, 16),
                secret = key.toByteArray(Charsets.UTF_8),
            ), input
        )
    }

    private fun generateKey(salt: ByteArray, secret: ByteArray): ByteArray {
        var key = md5(secret + salt)
        var currentKey = key
        while (currentKey.size < 48) {
            key = md5(key + secret + salt)
            currentKey += key
        }
        return currentKey
    }

    private fun md5(input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input)
    }

    private fun decryptSourceUrl(decryptionKey: ByteArray, sourceUrl: String): String {
        val cipherData = base64DecodeArray(sourceUrl)
        val encrypted = cipherData.copyOfRange(16, cipherData.size)
        val keyBytes = decryptionKey.copyOfRange(0, 32)
        val ivBytes = decryptionKey.copyOfRange(32, decryptionKey.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        val decryptedData = cipher.doFinal(encrypted)
        return String(decryptedData, Charsets.UTF_8)
    }

    @Serializable
    data class Tracks(
        @JsonProperty("file") @SerialName("file") val file: String? = null,
        @JsonProperty("label") @SerialName("label") val label: String? = null,
        @JsonProperty("kind") @SerialName("kind") val kind: String? = null,
    )

    @Serializable
    data class Sources(
        @JsonProperty("file") @SerialName("file") val file: String? = null,
        @JsonProperty("type") @SerialName("type") val type: String? = null,
        @JsonProperty("label") @SerialName("label") val label: String? = null,
    )

    @Serializable
    data class SourcesResponses(
        @JsonProperty("sources") @SerialName("sources") val sources: List<Sources?>? = emptyList(),
        @JsonProperty("tracks") @SerialName("tracks") val tracks: List<Tracks?>? = emptyList(),
    )

    @Serializable
    data class SourcesEncrypted(
        @JsonProperty("sources") @SerialName("sources") val sources: String? = null,
        @JsonProperty("encrypted") @SerialName("encrypted") val encrypted: Boolean? = null,
        @JsonProperty("tracks") @SerialName("tracks") val tracks: List<Tracks?>? = emptyList(),
    )
}

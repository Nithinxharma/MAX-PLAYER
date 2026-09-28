package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.utils.CryptoJSHelper

/**
 * Typealias and forwarders for CloudStream extractors requiring CryptoJSHelper
 * in `com.lagradost.cloudstream3.extractors.helper` namespace.
 */
typealias CryptoJSHelper = com.lagradost.cloudstream3.utils.CryptoJSHelper

@Suppress("unused", "FunctionName", "SameParameterValue")
object CryptoJS {
    fun encrypt(password: String, plainText: String): String = CryptoJSHelper.encrypt(password, plainText)
    fun decrypt(password: String, cipherText: String): String = CryptoJSHelper.decrypt(password, cipherText)
    fun decryptAesCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = CryptoJSHelper.decryptAesCbc(data, key, iv)
    fun decryptAesEcb(data: ByteArray, key: ByteArray): ByteArray = CryptoJSHelper.decryptAesEcb(data, key)
}

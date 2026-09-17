package com.lagradost.cloudstream3

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RobolectricBase64Test {
    @Test
    fun testBase64() {
        val result = Base64.encode("test".toByteArray(), Base64.DEFAULT)
        println("Base64 result: ${result?.let { String(it) } ?: "null"}")
    }
}

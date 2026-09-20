package com.lagradost.cloudstream3

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

class PluginRuntimeTest {

    @Test
    fun testCriticalClassesReflection() {
        val critical = listOf(
            "com.lagradost.cloudstream3.plugins.Plugin",
            "com.lagradost.cloudstream3.plugins.BasePlugin",
            "com.lagradost.cloudstream3.plugins.CloudstreamPlugin",
            "com.lagradost.cloudstream3.MainAPI",
            "com.lagradost.cloudstream3.utils.ExtractorApi"
        )

        critical.forEach { className ->
            try {
                val c = Class.forName(className)
                assertNotNull(c)
                println("SDK_CHECK -> FOUND: $className")
            } catch (e: Throwable) {
                println("SDK_CHECK -> MISSING: $className (Error: ${e.message})")
                throw e
            }
        }
    }

    @Test
    fun verifySuperstreamPluginLoading() {
        val jarFile = File("/tmp/Superstream.jar")
        if (!jarFile.exists()) return
        try {
            val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this.javaClass.classLoader)
            val pluginClass = classLoader.loadClass("com.hexated.SuperstreamPlugin")
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance()
            val loadMethod = pluginClass.getMethod("load", android.content.Context::class.java)
            
            println("Providers before load: ${APIHolder.apis.size}")
            loadMethod.invoke(pluginInstance, null)
            println("Providers after load: ${APIHolder.apis.size}")
            
            APIHolder.apis.forEach {
                println("- ${it.name}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


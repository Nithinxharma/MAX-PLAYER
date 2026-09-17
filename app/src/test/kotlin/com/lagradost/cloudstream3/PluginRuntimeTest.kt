package com.lagradost.cloudstream3
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

class PluginRuntimeTest {
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

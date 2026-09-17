package com.lagradost.cloudstream3

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLClassLoader
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VerifyProviderExecutionTest {
    @Test
    fun verifyExecution() = runBlocking {
        val jarFile = File("/tmp/Superstream.jar")
        if (!jarFile.exists()) {
            println("JAR not found.")
            return@runBlocking
        }
        
        try {
            val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this.javaClass.classLoader)
            val pluginClass = classLoader.loadClass("com.hexated.SuperstreamPlugin")
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance()
            val loadMethod = pluginClass.getMethod("load", android.content.Context::class.java)
            loadMethod.invoke(pluginInstance, null)
            
            val provider = APIHolder.apis.firstOrNull()
            if (provider == null) {
                println("No provider registered.")
                return@runBlocking
            }
            
            println("--- PROVIDER METADATA ---")
            println("Name: ${provider.name}")
            println("MainUrl: ${provider.mainUrl}")
            println("SupportedTypes: ${provider.supportedTypes}")
            println("Lang: ${provider.lang}")
            println("HasMainPage: ${provider.hasMainPage}")
            
            println("\n--- EXECUTING SEARCH ---")
            val searchResults = provider.search("Iron Man") ?: emptyList()
            println("Result Count: ${searchResults.size}")
            
            searchResults.take(10).forEachIndexed { index, res ->
                println("${index + 1}. Title: ${res.name}, Type: ${res.type}, URL: ${res.url}")
            }
            
            if (searchResults.isNotEmpty()) {
                val firstUrl = searchResults.first().url
                println("\n--- EXECUTING LOAD ---")
                println("Loading URL: $firstUrl")
                val loadResponse = provider.load(firstUrl)
                if (loadResponse != null) {
                    println("Title: ${loadResponse.name}")
                    println("Year: ${loadResponse.year}")
                    println("TvType: ${loadResponse.type}")
                    if (loadResponse is TvSeriesLoadResponse) {
                        println("Episodes count: ${loadResponse.episodes.size}")
                    }
                    println("Metadata: Tags=${loadResponse.tags}, Plot=${loadResponse.plot?.take(50)}...")
                } else {
                    println("Load response was null.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package xyz.mpv.rex.cinehub.extension.manager

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.MainAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtensionLoader {
    private const val TAG = "CineHub:ExtensionLoader"
    
    suspend fun loadPlugin(context: Context, pluginFile: File): List<MainAPI> = withContext(Dispatchers.IO) {
        val loadedProviders = mutableListOf<MainAPI>()
        try {
            val optDir = context.getDir("dex_cache", Context.MODE_PRIVATE)
            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                optDir.absolutePath,
                null,
                context.classLoader
            )
            
            val dexFile = DexFile(pluginFile)
            val entries = dexFile.entries()
            var pluginClass: Class<*>? = null
            
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                if (className.startsWith("com.lagradost.") || className.startsWith("kotlin.") || className.startsWith("org.jsoup.")) {
                    continue
                }
                
                try {
                    val clazz = classLoader.loadClass(className)
                    if (BasePlugin::class.java.isAssignableFrom(clazz) && !clazz.isInterface) {
                        pluginClass = clazz
                        break
                    }
                } catch (e: Throwable) {
                    // Ignore
                }
            }
            
            if (pluginClass != null) {
                Log.i(TAG, "Found Cloudstream Plugin class: ${pluginClass.name}")
                val pluginInstance = pluginClass.newInstance() as BasePlugin
                
                try {
                    pluginInstance.filename = pluginFile.absolutePath
                    pluginInstance.__filename = pluginFile.absolutePath
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set filename on BasePlugin")
                }
                
                // CloudStream APIHolder will hold the providers
                val beforeProviders = com.lagradost.cloudstream3.APIHolder.allProviders.toList()
                pluginInstance.load()
                
                val afterProviders = com.lagradost.cloudstream3.APIHolder.allProviders.toList()
                loadedProviders.addAll(afterProviders.subtract(beforeProviders.toSet()))
                Log.i(TAG, "Successfully loaded plugin. Found ${loadedProviders.size} providers.")
            } else {
                Log.e(TAG, "No Cloudstream Plugin class found in ${pluginFile.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin from ${pluginFile.name}", e)
        }
        loadedProviders
    }
}

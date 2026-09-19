package xyz.mpv.rex.cinehub.extension.registry

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * ProviderRegistry manages registered CineHub content providers,
 * active states, and lifecycle.
 */
class ProviderRegistry {
    private val TAG = "ProviderRegistry"
    private val allProviders = ConcurrentHashMap<String, CineHubProvider>()
    private val enabledProviderIds = ConcurrentHashMap.newKeySet<String>()

    private val _activeProviders = MutableStateFlow<List<CineHubProvider>>(emptyList())
    val activeProviders: StateFlow<List<CineHubProvider>> = _activeProviders.asStateFlow()

    private val _registeredProviders = MutableStateFlow<List<CineHubProvider>>(emptyList())
    val registeredProviders: StateFlow<List<CineHubProvider>> = _registeredProviders.asStateFlow()

    init {
        Log.i(TAG, "INSTANCE_IDENTITY: ProviderRegistry initialized. identityHashCode=${System.identityHashCode(this)}")
    }

    fun register(provider: CineHubProvider, isEnabledByDefault: Boolean = true) {
        Log.i(TAG, "INSTANCE_IDENTITY: ProviderRegistry.register called on ProviderRegistry@${System.identityHashCode(this)} for provider ${provider.name} (id=${provider.id})")
        allProviders[provider.id] = provider
        if (isEnabledByDefault) {
            enabledProviderIds.add(provider.id)
        }
        Log.i(TAG, "Registered provider: ${provider.name} (id=${provider.id}), isEnabledByDefault=$isEnabledByDefault [Total: ${allProviders.size}, Enabled: ${enabledProviderIds.size}]")
        updateFlows()
    }

    fun unregister(identifier: String) {
        val matchingIds = findMatchingProviderIds(identifier)
        for (id in matchingIds) {
            allProviders.remove(id)
            enabledProviderIds.remove(id)
        }
        allProviders.remove(identifier)
        enabledProviderIds.remove(identifier)
        Log.i(TAG, "Unregistered provider identifier: $identifier [Removed: ${matchingIds.size}]")
        updateFlows()
    }

    fun setProviderEnabled(identifier: String, enabled: Boolean) {
        val matchingIds = findMatchingProviderIds(identifier)
        if (matchingIds.isNotEmpty()) {
            for (id in matchingIds) {
                if (enabled) {
                    enabledProviderIds.add(id)
                } else {
                    enabledProviderIds.remove(id)
                }
            }
            Log.i(TAG, "setProviderEnabled($identifier, $enabled) matched IDs: $matchingIds")
        } else {
            // Direct key fallback if registered under direct ID
            if (enabled) {
                if (allProviders.containsKey(identifier)) {
                    enabledProviderIds.add(identifier)
                }
            } else {
                enabledProviderIds.remove(identifier)
            }
            Log.w(TAG, "setProviderEnabled($identifier, $enabled) fallback executed. Matching ID count: 0")
        }
        updateFlows()
    }

    fun isProviderEnabled(identifier: String): Boolean {
        if (enabledProviderIds.contains(identifier)) return true
        val matchingIds = findMatchingProviderIds(identifier)
        return matchingIds.any { enabledProviderIds.contains(it) }
    }

    fun getProvider(identifier: String): CineHubProvider? {
        return allProviders[identifier] ?: run {
            val matchingIds = findMatchingProviderIds(identifier)
            matchingIds.firstNotNullOfOrNull { allProviders[it] }
        }
    }

    fun getEnabledProviders(): List<CineHubProvider> {
        return allProviders.values.filter { enabledProviderIds.contains(it.id) }
    }

    fun getAllProviders(): List<CineHubProvider> {
        return allProviders.values.toList()
    }

    private fun findMatchingProviderIds(identifier: String): Set<String> {
        val clean = identifier.trim()
        val normalized = clean.lowercase().replace("\\s+".toRegex(), "_")
        return allProviders.values.filter { provider ->
            val pId = provider.id.lowercase()
            val pName = provider.name.lowercase()
            pId == clean.lowercase() ||
            pName == clean.lowercase() ||
            pId == "cs3_$normalized" ||
            pId == "cs3_${clean.lowercase()}" ||
            pName.replace("\\s+".toRegex(), "_") == normalized ||
            pName.contains(clean.removePrefix("cs3_").removeSuffix("Provider"), ignoreCase = true) ||
            clean.contains(provider.name, ignoreCase = true)
        }.map { it.id }.toSet()
    }

    private fun updateFlows() {
        val all = allProviders.values.toList()
        _registeredProviders.value = all
        _activeProviders.value = all.filter { enabledProviderIds.contains(it.id) }
    }
}

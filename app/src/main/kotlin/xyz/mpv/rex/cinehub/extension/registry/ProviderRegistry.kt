package xyz.mpv.rex.cinehub.extension.registry

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
    private val allProviders = ConcurrentHashMap<String, CineHubProvider>()
    private val enabledProviderIds = ConcurrentHashMap.newKeySet<String>()

    private val _activeProviders = MutableStateFlow<List<CineHubProvider>>(emptyList())
    val activeProviders: StateFlow<List<CineHubProvider>> = _activeProviders.asStateFlow()

    private val _registeredProviders = MutableStateFlow<List<CineHubProvider>>(emptyList())
    val registeredProviders: StateFlow<List<CineHubProvider>> = _registeredProviders.asStateFlow()

    fun register(provider: CineHubProvider, isEnabledByDefault: Boolean = true) {
        allProviders[provider.id] = provider
        if (isEnabledByDefault) {
            enabledProviderIds.add(provider.id)
        }
        updateFlows()
    }

    fun unregister(providerId: String) {
        val targets = allProviders.keys.filter {
            it.equals(providerId, ignoreCase = true) ||
            it.equals("cs3_${providerId.lowercase()}", ignoreCase = true) ||
            allProviders[it]?.name.equals(providerId, ignoreCase = true)
        }
        for (target in targets) {
            allProviders.remove(target)
            enabledProviderIds.remove(target)
        }
        allProviders.remove(providerId)
        enabledProviderIds.remove(providerId)
        updateFlows()
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val matchingKeys = allProviders.keys.filter {
            it.equals(providerId, ignoreCase = true) ||
            it.equals("cs3_${providerId.lowercase()}", ignoreCase = true) ||
            allProviders[it]?.name.equals(providerId, ignoreCase = true)
        }
        if (matchingKeys.isNotEmpty()) {
            for (key in matchingKeys) {
                if (enabled) enabledProviderIds.add(key)
                else enabledProviderIds.remove(key)
            }
        } else {
            if (enabled && allProviders.containsKey(providerId)) {
                enabledProviderIds.add(providerId)
            } else {
                enabledProviderIds.remove(providerId)
            }
        }
        updateFlows()
    }

    fun isProviderEnabled(providerId: String): Boolean {
        if (enabledProviderIds.contains(providerId)) return true
        val matched = allProviders.entries.firstOrNull {
            it.key.equals(providerId, ignoreCase = true) ||
            it.key.equals("cs3_${providerId.lowercase()}", ignoreCase = true) ||
            it.value.name.equals(providerId, ignoreCase = true)
        }
        return matched != null && enabledProviderIds.contains(matched.key)
    }

    fun getProvider(providerId: String): CineHubProvider? {
        return allProviders[providerId]
            ?: allProviders.values.firstOrNull {
                it.id.equals(providerId, ignoreCase = true) ||
                it.id.equals("cs3_${providerId.lowercase()}", ignoreCase = true) ||
                it.name.equals(providerId, ignoreCase = true)
            }
    }

    fun getEnabledProviders(): List<CineHubProvider> {
        return allProviders.values.filter { enabledProviderIds.contains(it.id) }
    }

    fun getAllProviders(): List<CineHubProvider> {
        return allProviders.values.toList()
    }

    private fun updateFlows() {
        val all = allProviders.values.toList()
        _registeredProviders.value = all
        _activeProviders.value = all.filter { enabledProviderIds.contains(it.id) }
    }
}

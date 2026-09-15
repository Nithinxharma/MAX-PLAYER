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
        android.util.Log.i("ProviderRegistry", "[PROVIDER] Registered: ${provider.name} (id=${provider.id})")
        allProviders[provider.id] = provider
        if (isEnabledByDefault) {
            enabledProviderIds.add(provider.id)
        }
        updateFlows()
    }

    fun unregister(providerId: String) {
        allProviders.remove(providerId)
        enabledProviderIds.remove(providerId)
        updateFlows()
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        if (enabled) {
            if (allProviders.containsKey(providerId)) {
                enabledProviderIds.add(providerId)
            }
        } else {
            enabledProviderIds.remove(providerId)
        }
        updateFlows()
    }

    fun isProviderEnabled(providerId: String): Boolean {
        return enabledProviderIds.contains(providerId)
    }

    fun getProvider(providerId: String): CineHubProvider? {
        return allProviders[providerId]
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

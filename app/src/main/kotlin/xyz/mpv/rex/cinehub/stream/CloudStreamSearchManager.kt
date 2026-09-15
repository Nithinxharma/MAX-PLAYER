package xyz.mpv.rex.cinehub.stream

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry

/**
 * CloudStream Provider Search Manager.
 */
class CloudStreamSearchManager(
    private val providerRegistry: ProviderRegistry
) {
    fun searchAll(query: String): Flow<List<CineHubSearchItem>> = flow {
        val providers = providerRegistry.getEnabledProviders()
        val allResults = mutableListOf<CineHubSearchItem>()
        providers.forEach { provider ->
            try {
                val results = provider.search(query)
                if (results.isNotEmpty()) {
                    allResults.addAll(results)
                    emit(allResults.toList())
                }
            } catch (e: Exception) {
                // Ignore individual provider error
            }
        }
        if (allResults.isEmpty()) {
            emit(emptyList())
        }
    }
}


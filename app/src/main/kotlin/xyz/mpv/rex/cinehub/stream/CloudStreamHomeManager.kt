package xyz.mpv.rex.cinehub.stream

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList
import xyz.mpv.rex.cinehub.extension.api.CineHubProvider
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry

/**
 * Manages loading and caching of dynamic Home Page sections
 * provided by active CloudStream providers.
 */
class CloudStreamHomeManager(
    private val providerRegistry: ProviderRegistry
) {
    private val TAG = "CineHub:HomeManager"

    fun loadHome(): Flow<List<CineHubHomePageList>> = flow {
        val providers = providerRegistry.getEnabledProviders()
        if (providers.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val allSections = mutableListOf<CineHubHomePageList>()

        // Concurrently query providers for their home sections
        val sections = withContext(Dispatchers.IO) {
            val deferreds = providers.map { provider ->
                async {
                    loadFromProvider(provider)
                }
            }
            deferreds.awaitAll().flatten()
        }

        allSections.addAll(sections)
        emit(allSections)
    }

    suspend fun loadAllHomePages(): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        val providers = providerRegistry.getEnabledProviders()
        val deferreds = providers.map { provider ->
            async {
                loadFromProvider(provider)
            }
        }
        deferreds.awaitAll().flatten()
    }

    suspend fun loadFromProvider(provider: CineHubProvider): List<CineHubHomePageList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading home rows from '${provider.name}'...")
            val rows = provider.getHomePage()
            Log.i(TAG, "Provider '${provider.name}' delivered ${rows.size} home row(s)")
            rows
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading home rows from '${provider.name}': ${e.message}")
            emptyList()
        }
    }
}


cat << 'INNER_EOF' > app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamHomeManager.kt
package xyz.mpv.rex.cinehub.stream

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.mpv.rex.cinehub.extension.registry.ProviderRegistry
import xyz.mpv.rex.cinehub.extension.api.CineHubHomePageList

class CloudStreamHomeManager(
    private val providerRegistry: ProviderRegistry
) {
    fun loadHome(): Flow<List<CineHubHomePageList>> = flow {
        val providers = providerRegistry.getEnabledProviders()
        val allContent = mutableListOf<CineHubHomePageList>()
        providers.forEach { provider ->
            try {
                if (provider.hasMainPage) {
                    val homeLists = provider.getHomePage()
                    allContent.addAll(homeLists)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        emit(allContent)
    }
}
INNER_EOF

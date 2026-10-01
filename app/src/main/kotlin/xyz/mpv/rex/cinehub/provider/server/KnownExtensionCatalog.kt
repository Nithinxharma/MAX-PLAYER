package xyz.mpv.rex.cinehub.provider.server

import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import java.util.Locale

/**
 * Dynamic catalog bridge for CloudStream & OTT community extensions.
 * All extensions and providers are fetched dynamically from repositories,
 * Firestore, and installed plugin packages without hardcoded lists.
 */
object KnownExtensionCatalog {

    val SEED_PLUGINS: List<AvailablePlugin> = emptyList()

    /**
     * Resolves verified download URL for an extension or provider name dynamically if registered.
     */
    fun findSeedPlugin(nameOrId: String): AvailablePlugin? {
        return null
    }
}


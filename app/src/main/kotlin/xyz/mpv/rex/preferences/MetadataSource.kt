package xyz.mpv.rex.preferences

/**
 * Source of rich media metadata used for search indexing and details view.
 */
enum class MetadataSource {
    /**
     * TMDB: Search results and details pages are enriched with TMDB artwork, plot, ratings, and actors.
     */
    TMDB,

    /**
     * PROVIDER: Search results and details pages display metadata exactly as fetched from the original provider.
     */
    PROVIDER
}

package xyz.mpv.rex.cinehub.provider.server

import xyz.mpv.rex.cinehub.extension.model.AvailablePlugin
import java.util.Locale

/**
 * Built-in resilient seed catalog for high-demand CloudStream & OTT community extensions.
 *
 * Guarantees that when an administrator assigns an extension by name or provider name
 * in Plan Management (e.g. Bollyflix, SuperStream, CineStream, VegaMovies, Moviesmod, AllMovieLand),
 * the direct verified download URL and package metadata are immediately resolvable,
 * even before remote repository network scans complete or during slow connections.
 */
object KnownExtensionCatalog {

    val SEED_PLUGINS: List<AvailablePlugin> = listOf(
        AvailablePlugin(
            name = "Bollyflix",
            internalName = "Bollyflix",
            version = "33",
            versionCode = 33,
            description = "High-speed Indian Movies and Series up to 4K",
            url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/Bollyflix.cs3",
            repositoryUrl = "https://github.com/SaurabhKaperwan/CSX",
            iconUrl = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/refs/heads/master/Bollyflix/icon.png",
            authors = listOf("megix"),
            tvTypes = listOf("Movie", "TvSeries", "AsianDrama", "Anime"),
            lang = "hi",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "SuperStream",
            internalName = "SuperStream",
            version = "1.2.0",
            versionCode = 12,
            description = "Fast OTT movie and series streaming index.",
            url = "https://raw.githubusercontent.com/recloudstream/extensions/master/SuperStream.cs3",
            repositoryUrl = "https://github.com/recloudstream/extensions",
            authors = listOf("CloudStream"),
            tvTypes = listOf("Movie", "TvSeries"),
            lang = "en",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "CineStream",
            internalName = "CineStream",
            version = "487",
            versionCode = 487,
            description = "One stop solution for Movies, Series, Anime, AsianDrama and Torrents",
            url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CineStream.cs3",
            repositoryUrl = "https://github.com/SaurabhKaperwan/CSX",
            authors = listOf("megix"),
            tvTypes = listOf("Movie", "TvSeries", "Anime"),
            lang = "en",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "VegaMovies",
            internalName = "VegaMovies",
            version = "32",
            versionCode = 32,
            description = "VegaMovies direct high-quality streaming provider",
            url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/VegaMovies.cs3",
            repositoryUrl = "https://github.com/SaurabhKaperwan/CSX",
            authors = listOf("megix"),
            tvTypes = listOf("Movie", "TvSeries"),
            lang = "hi",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "Moviesmod",
            internalName = "Moviesmod",
            version = "18",
            versionCode = 18,
            description = "Moviesmod dual-audio Bollywood, Hollywood, and Web Series",
            url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/Moviesmod.cs3",
            repositoryUrl = "https://github.com/SaurabhKaperwan/CSX",
            authors = listOf("megix"),
            tvTypes = listOf("Movie", "TvSeries"),
            lang = "hi",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "MoviesDrive",
            internalName = "MoviesDrive",
            version = "21",
            versionCode = 21,
            description = "MoviesDrive fast streaming & high-speed media links",
            url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/MoviesDrive.cs3",
            repositoryUrl = "https://github.com/SaurabhKaperwan/CSX",
            authors = listOf("megix"),
            tvTypes = listOf("Movie", "TvSeries"),
            lang = "hi",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "AllMovieLandProvider",
            internalName = "AllMovieLandProvider",
            version = "25",
            versionCode = 25,
            description = "Indian MultiLanguage Provider (Mostly Hindi)",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/AllMovieLandProvider.cs3",
            repositoryUrl = "https://github.com/phisher98/cloudstream-extensions-phisher",
            authors = listOf("Phisher98"),
            tvTypes = listOf("Movie", "TvSeries", "Cartoon"),
            lang = "hi",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "AllWish",
            internalName = "AllWish",
            version = "18",
            versionCode = 18,
            description = "Anime streaming from all-wish",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/AllWish.cs3",
            repositoryUrl = "https://github.com/phisher98/cloudstream-extensions-phisher",
            authors = listOf("Phisher98"),
            tvTypes = listOf("Anime"),
            lang = "en",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "AnimePahe",
            internalName = "AnimePahe",
            version = "21",
            versionCode = 21,
            description = "AnimePahe fast anime series streaming",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/AnimePahe.cs3",
            repositoryUrl = "https://github.com/phisher98/cloudstream-extensions-phisher",
            authors = listOf("Phisher98"),
            tvTypes = listOf("Anime"),
            lang = "en",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "DailymotionProvider",
            internalName = "DailymotionProvider",
            version = "4",
            versionCode = 4,
            description = "Watch content from Dailymotion",
            url = "https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.cs3",
            repositoryUrl = "https://github.com/recloudstream/extensions",
            authors = listOf("Luna712"),
            tvTypes = listOf("Others"),
            lang = "en",
            isEnabled = true
        ),
        AvailablePlugin(
            name = "CastleTV",
            internalName = "castletv",
            version = "2.4.0",
            versionCode = 24,
            description = "High-definition Live TV, Sports, and Movies feed.",
            url = "https://raw.githubusercontent.com/recloudstream/extensions/master/CastleTV.cs3",
            repositoryUrl = "server://maxstream.cloud",
            authors = listOf("MaxStream"),
            tvTypes = listOf("Live", "Movie", "TvSeries"),
            lang = "en",
            isEnabled = true
        )
    )

    /**
     * Resolves verified download URL for an extension or provider name.
     */
    fun findSeedPlugin(nameOrId: String): AvailablePlugin? {
        val clean = nameOrId.trim().lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
        if (clean.isBlank()) return null
        return SEED_PLUGINS.firstOrNull { plugin ->
            val pName = plugin.name.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
            val pInternal = plugin.internalName.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
            pName == clean || pInternal == clean ||
                    pName.removeSuffix("provider") == clean ||
                    clean.removeSuffix("provider") == pName ||
                    pInternal.removeSuffix("provider") == clean ||
                    clean.removeSuffix("provider") == pInternal
        }
    }
}

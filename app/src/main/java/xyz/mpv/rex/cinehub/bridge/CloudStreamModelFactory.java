package xyz.mpv.rex.cinehub.bridge;

import com.lagradost.cloudstream3.*;
import com.lagradost.cloudstream3.utils.ExtractorLink;
import com.lagradost.cloudstream3.utils.ExtractorLinkType;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Java helper factory that provides clean, direct instantiation of CloudStream
 * core models without tripping Kotlin DeprecationLevel.ERROR compiler blocks.
 */
public final class CloudStreamModelFactory {
    private CloudStreamModelFactory() {}

    public static MovieLoadResponse createMovie(
        String name,
        String url,
        String apiName,
        TvType type,
        String dataUrl,
        String posterUrl,
        Integer year,
        String plot,
        String backgroundPosterUrl
    ) {
        return new MovieLoadResponse(
            name,
            url,
            apiName,
            type != null ? type : TvType.Movie,
            dataUrl != null ? dataUrl : url,
            posterUrl,
            year,
            plot,
            null, // score
            Collections.emptyList(), // tags
            null, // duration
            Collections.emptyList(), // trailers
            Collections.emptyList(), // recommendations
            Collections.emptyList(), // actors
            false, // comingSoon
            Collections.emptyMap(), // syncData
            Collections.emptyMap(), // posterHeaders
            backgroundPosterUrl != null ? backgroundPosterUrl : posterUrl,
            null, // logoUrl
            null, // contentRating
            url != null ? url : "" // uniqueUrl
        );
    }

    public static TvSeriesLoadResponse createTvSeries(
        String name,
        String url,
        String apiName,
        TvType type,
        List<Episode> episodes,
        String posterUrl,
        Integer year,
        String plot,
        String backgroundPosterUrl
    ) {
        return new TvSeriesLoadResponse(
            name,
            url,
            apiName,
            type != null ? type : TvType.TvSeries,
            episodes != null ? episodes : Collections.emptyList(),
            posterUrl,
            year,
            plot,
            null, // showStatus
            null, // score
            Collections.emptyList(), // tags
            null, // duration
            Collections.emptyList(), // trailers
            Collections.emptyList(), // recommendations
            Collections.emptyList(), // actors
            false, // comingSoon
            Collections.emptyMap(), // syncData
            Collections.emptyMap(), // posterHeaders
            null, // nextAiring
            Collections.emptyList(), // seasonNames
            backgroundPosterUrl != null ? backgroundPosterUrl : posterUrl,
            null, // logoUrl
            null, // contentRating
            url != null ? url : "" // uniqueUrl
        );
    }

    public static Episode createEpisode(
        String data,
        String name,
        Integer season,
        Integer episode,
        String posterUrl,
        String description
    ) {
        return new Episode(
            data,
            name,
            season,
            episode,
            posterUrl,
            null, // score
            description,
            null, // date
            null  // runTime
        );
    }

    public static ExtractorLink createExtractorLink(
        String source,
        String name,
        String url,
        String referer,
        int quality,
        ExtractorLinkType type,
        Map<String, String> headers
    ) {
        return new ExtractorLink(
            source != null ? source : "Extension",
            name != null ? name : "Auto",
            url != null ? url : "",
            referer != null ? referer : "",
            quality,
            type != null ? type : ExtractorLinkType.VIDEO,
            headers != null ? headers : Collections.emptyMap(),
            null
        );
    }
}

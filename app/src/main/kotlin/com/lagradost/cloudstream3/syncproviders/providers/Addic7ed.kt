package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.SubtitleHelper
import org.jsoup.Jsoup

class Addic7ed : SubtitleAPI() {
    override val name = "Addic7ed"
    override val idPrefix = "addic7ed"
    override val mainUrl = "https://www.addic7ed.com"

    override suspend fun search(query: SubtitleSearch): List<SubtitleEntity>? {
        val s = query.seasonNumber ?: return null
        val ep = query.epNumber ?: return null
        val queryStr = "${query.query} ${s}x${if (ep < 10) "0$ep" else "$ep"}"
        val url = "$mainUrl/search.php?search=${java.net.URLEncoder.encode(queryStr, "UTF-8")}&Submit=Search"
        val doc = app.get(url).document
        val rows = doc.select("table.tabel tr.epeven, table.tabel tr.epodd")
        return rows.mapNotNull { row ->
            val downloadLink = row.selectFirst("a[href^=/updated/], a[href^=/original/]")?.attr("href") ?: return@mapNotNull null
            val lang = row.selectFirst("td.language")?.text()?.trim() ?: "English"
            val version = row.selectFirst("td.newsDate")?.text()?.trim() ?: query.query
            SubtitleEntity(
                idPrefix = idPrefix,
                name = "${query.query} - S${s}E${ep} ($version)",
                lang = lang,
                data = "$mainUrl$downloadLink",
                source = name,
                epNumber = ep,
                seasonNumber = s,
                isHearingImpaired = row.selectFirst("img[title~=Hearing Impaired]") != null
            )
        }
    }

    override suspend fun load(data: SubtitleEntity): SubtitleResource? {
        val url = data.data
        if (url.isBlank()) return null
        val resource = SubtitleResource()
        resource.addUrl(url, data.name)
        return resource
    }
}

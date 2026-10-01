package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Stream
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.Unpacker
import com.lagradost.cloudstream3.utils.registerExtractor

open class StreamTape : ExtractorApi() {
    override val name = "StreamTape"
    override val mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val targetRegex = Regex("""robotlink'\)\.innerHTML\s*=\s*'([^']+)'\s*\+\s*'([^']+)'""")
        val match = targetRegex.find(res)
        if (match != null) {
            val streamUrl = "https:${match.groupValues[1]}${match.groupValues[2]}"
            callback(
                ExtractorLink(
                    source = name,
                    name = "$name (1080p)",
                    url = streamUrl,
                    referer = url,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.VIDEO
                )
            )
        }
    }
}

open class MixDrop : ExtractorApi() {
    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.ag"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val unpacked = Unpacker.unpack(res) ?: res
        val regex = Regex("""MDCore\.wurl\s*=\s*"([^"]+)"""")
        val match = regex.find(unpacked)
        if (match != null) {
            val streamUrl = if (match.groupValues[1].startsWith("//")) "https:${match.groupValues[1]}" else match.groupValues[1]
            callback(
                ExtractorLink(
                    source = name,
                    name = "$name (1080p)",
                    url = streamUrl,
                    referer = url,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.VIDEO
                )
            )
        }
    }
}

open class DoodStream : ExtractorApi() {
    override val name = "DoodStream"
    override val mainUrl = "https://dood.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val passRegex = Regex("""/pass_md5/[^']*""")
        val passMatch = passRegex.find(res)
        if (passMatch != null) {
            val passUrl = "https://dood.to${passMatch.value}"
            val trueUrl = app.get(passUrl, referer = url).text
            val token = System.currentTimeMillis().toString()
            val finalUrl = "$trueUrl$token?token=${passMatch.value.substringAfterLast("/")}&expiry=$token"
            callback(
                ExtractorLink(
                    source = name,
                    name = "$name (720p)",
                    url = finalUrl,
                    referer = url,
                    quality = Qualities.P720.value,
                    type = ExtractorLinkType.VIDEO
                )
            )
        }
    }
}

typealias DoodExtractor = DoodStream
typealias DoodLaExtractor = DoodStream

open class StreamWish : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val unpacked = Unpacker.unpack(res) ?: res
        val m3u8Match = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class Filemoon : ExtractorApi() {
    override val name = "Filemoon"
    override val mainUrl = "https://filemoon.sx"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val unpacked = Unpacker.unpack(res) ?: res
        val m3u8Match = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val m3u8Match = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(res)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val hlsRegex = Regex("""'hls':\s*'([^']+)'""")
        val hlsMatch = hlsRegex.find(res)
        if (hlsMatch != null) {
            val streamUrl = hlsMatch.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class LuluStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://luluvdo.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val unpacked = Unpacker.unpack(res) ?: res
        val m3u8Match = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class RapidCloud : ExtractorApi() {
    override val name = "RapidCloud"
    override val mainUrl = "https://rapid-cloud.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val m3u8Match = Regex("""(?:file|source)\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(res)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class SuperStreamExtractor : ExtractorApi() {
    override val name = "SuperStream"
    override val mainUrl = "https://superstream.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (url.contains(".m3u8")) {
            M3u8Helper.m3u8Generation(M3u8Stream(url), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class Upstream : ExtractorApi() {
    override val name = "Upstream"
    override val mainUrl = "https://upstream.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val unpacked = Unpacker.unpack(res) ?: res
        val m3u8Match = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            M3u8Helper.m3u8Generation(M3u8Stream(streamUrl, mapOf("Referer" to url)), returnAll = true).forEach {
                callback(it.copy(source = name))
            }
        }
    }
}

open class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://mp4upload.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val videoMatch = Regex("""player\.src\("([^"]+)""").find(res)
        if (videoMatch != null) {
            callback(
                ExtractorLink(
                    source = name,
                    name = "$name (1080p)",
                    url = videoMatch.groupValues[1],
                    referer = url,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.VIDEO
                )
            )
        }
    }
}

object DefaultExtractors {
    fun registerAll() {
        registerExtractor(StreamTape())
        registerExtractor(MixDrop())
        registerExtractor(DoodStream())
        registerExtractor(StreamWish())
        registerExtractor(Filemoon())
        registerExtractor(Vidmoly())
        registerExtractor(Voe())
        registerExtractor(LuluStream())
        registerExtractor(RapidCloud())
        registerExtractor(SuperStreamExtractor())
        registerExtractor(Upstream())
        registerExtractor(Mp4Upload())
    }
}

package com.lagradost.cloudstream3.utils

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

data class MpdManifest(
    val baseUrl: String?,
    val periods: List<MpdPeriod>
)

data class MpdPeriod(
    val id: String?,
    val duration: String?,
    val baseUrl: String?,
    val adaptationSets: List<MpdAdaptationSet>
)

data class MpdAdaptationSet(
    val id: String?,
    val mimeType: String?,
    val contentType: String?,
    val lang: String?,
    val baseUrl: String?,
    val representations: List<MpdRepresentation>,
    val segmentTemplate: MpdSegmentTemplate? = null
)

data class MpdRepresentation(
    val id: String,
    val bandwidth: Long,
    val width: Int?,
    val height: Int?,
    val codecs: String?,
    val mimeType: String?,
    val baseUrl: String?,
    val segmentTemplate: MpdSegmentTemplate? = null
)

data class MpdSegmentTemplate(
    val media: String?,
    val initialization: String?,
    val timescale: Long?,
    val timeline: List<MpdSegmentTimelineEntry> = emptyList()
)

data class MpdSegmentTimelineEntry(
    val startTime: Long?,
    val duration: Long,
    val repeat: Int = 0
)

object MpdManifestParser {

    private fun resolveUrl(base: String, relative: String): String {
        return try {
            if (relative.startsWith("http://") || relative.startsWith("https://")) {
                relative
            } else {
                URI(base).resolve(relative).toString()
            }
        } catch (_: Exception) {
            if (relative.startsWith("/")) {
                val protoEnd = base.indexOf("://")
                if (protoEnd != -1) {
                    val hostEnd = base.indexOf('/', protoEnd + 3)
                    val origin = if (hostEnd != -1) base.substring(0, hostEnd) else base
                    "$origin$relative"
                } else relative
            } else {
                val parent = base.substringBeforeLast('/')
                "$parent/$relative"
            }
        }
    }

    private fun getChildElementsByTagName(parent: Element, tagName: String): List<Element> {
        val list = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val el = child as Element
                val local = el.localName ?: el.tagName
                if (local.equals(tagName, ignoreCase = true) || el.tagName.equals(tagName, ignoreCase = true)) {
                    list.add(el)
                }
            }
        }
        return list
    }

    private fun getFirstChildText(parent: Element, tagName: String): String? {
        return getChildElementsByTagName(parent, tagName).firstOrNull()?.textContent?.trim()
    }

    fun parse(xmlContent: String, sourceUrl: String): MpdManifest {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Throwable) {
                // Ignore if security features unsupported by XML parser
            }
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        val rootBaseUrlText = getFirstChildText(root, "BaseURL")
        val rootBaseUrl = if (!rootBaseUrlText.isNullOrBlank()) {
            resolveUrl(sourceUrl, rootBaseUrlText)
        } else {
            sourceUrl
        }

        val periodElements = getChildElementsByTagName(root, "Period")
        val periods = mutableListOf<MpdPeriod>()

        val defaultPeriodElements = if (periodElements.isEmpty()) listOf(root) else periodElements

        for (periodEl in defaultPeriodElements) {
            val periodId = periodEl.getAttribute("id").takeIf { it.isNotBlank() }
            val periodDuration = periodEl.getAttribute("duration").takeIf { it.isNotBlank() }
            val periodBaseText = getFirstChildText(periodEl, "BaseURL")
            val periodBaseUrl = if (!periodBaseText.isNullOrBlank()) {
                resolveUrl(rootBaseUrl, periodBaseText)
            } else {
                rootBaseUrl
            }

            val adaptationElements = getChildElementsByTagName(periodEl, "AdaptationSet")
            val adaptationSets = mutableListOf<MpdAdaptationSet>()

            for (adaptEl in adaptationElements) {
                val adaptId = adaptEl.getAttribute("id").takeIf { it.isNotBlank() }
                val adaptMime = adaptEl.getAttribute("mimeType").takeIf { it.isNotBlank() }
                val adaptContent = adaptEl.getAttribute("contentType").takeIf { it.isNotBlank() }
                val adaptLang = adaptEl.getAttribute("lang").takeIf { it.isNotBlank() }
                val adaptBaseText = getFirstChildText(adaptEl, "BaseURL")
                val adaptBaseUrl = if (!adaptBaseText.isNullOrBlank()) {
                    resolveUrl(periodBaseUrl, adaptBaseText)
                } else {
                    periodBaseUrl
                }

                val adaptTemplateEl = getChildElementsByTagName(adaptEl, "SegmentTemplate").firstOrNull()
                val adaptTemplate = parseSegmentTemplate(adaptTemplateEl)

                val repElements = getChildElementsByTagName(adaptEl, "Representation")
                val representations = mutableListOf<MpdRepresentation>()

                for (repEl in repElements) {
                    val repId = repEl.getAttribute("id").ifBlank { "rep_${representations.size}" }
                    val bandwidth = repEl.getAttribute("bandwidth").toLongOrNull() ?: 0L
                    val width = repEl.getAttribute("width").toIntOrNull()
                    val height = repEl.getAttribute("height").toIntOrNull()
                    val codecs = repEl.getAttribute("codecs").takeIf { it.isNotBlank() }
                    val repMime = repEl.getAttribute("mimeType").takeIf { it.isNotBlank() } ?: adaptMime
                    val repBaseText = getFirstChildText(repEl, "BaseURL")
                    val repBaseUrl = if (!repBaseText.isNullOrBlank()) {
                        resolveUrl(adaptBaseUrl, repBaseText)
                    } else {
                        adaptBaseUrl
                    }

                    val repTemplateEl = getChildElementsByTagName(repEl, "SegmentTemplate").firstOrNull()
                    val repTemplate = parseSegmentTemplate(repTemplateEl) ?: adaptTemplate

                    representations.add(
                        MpdRepresentation(
                            id = repId,
                            bandwidth = bandwidth,
                            width = width,
                            height = height,
                            codecs = codecs,
                            mimeType = repMime,
                            baseUrl = repBaseUrl,
                            segmentTemplate = repTemplate
                        )
                    )
                }

                adaptationSets.add(
                    MpdAdaptationSet(
                        id = adaptId,
                        mimeType = adaptMime,
                        contentType = adaptContent,
                        lang = adaptLang,
                        baseUrl = adaptBaseUrl,
                        representations = representations,
                        segmentTemplate = adaptTemplate
                    )
                )
            }

            periods.add(
                MpdPeriod(
                    id = periodId,
                    duration = periodDuration,
                    baseUrl = periodBaseUrl,
                    adaptationSets = adaptationSets
                )
            )
        }

        return MpdManifest(
            baseUrl = rootBaseUrl,
            periods = periods
        )
    }

    private fun parseSegmentTemplate(el: Element?): MpdSegmentTemplate? {
        if (el == null) return null
        val media = el.getAttribute("media").takeIf { it.isNotBlank() }
        val init = el.getAttribute("initialization").takeIf { it.isNotBlank() }
        val timescale = el.getAttribute("timescale").toLongOrNull()

        val timelineEl = getChildElementsByTagName(el, "SegmentTimeline").firstOrNull()
        val timelineList = mutableListOf<MpdSegmentTimelineEntry>()
        if (timelineEl != null) {
            val sElements = getChildElementsByTagName(timelineEl, "S")
            for (s in sElements) {
                val t = s.getAttribute("t").toLongOrNull()
                val d = s.getAttribute("d").toLongOrNull() ?: 0L
                val r = s.getAttribute("r").toIntOrNull() ?: 0
                timelineList.add(MpdSegmentTimelineEntry(startTime = t, duration = d, repeat = r))
            }
        }

        return MpdSegmentTemplate(
            media = media,
            initialization = init,
            timescale = timescale,
            timeline = timelineList
        )
    }
}

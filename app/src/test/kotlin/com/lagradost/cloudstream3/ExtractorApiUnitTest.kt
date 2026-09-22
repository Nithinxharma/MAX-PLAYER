package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorApiUnitTest {

    @Test
    fun testHttpsify() {
        assertEquals("https://example.com/test.m3u8", httpsify("//example.com/test.m3u8"))
        assertEquals("https://example.com/test.m3u8", httpsify("https://example.com/test.m3u8"))
        assertEquals("http://example.com/test.m3u8", httpsify("http://example.com/test.m3u8"))
    }

    @Test
    fun testGetQualityFromName() {
        assertEquals(Qualities.P1080.value, getQualityFromName("1080p"))
        assertEquals(Qualities.P720.value, getQualityFromName("720p HD"))
        assertEquals(Qualities.P2160.value, getQualityFromName("4K UHD"))
        assertEquals(Qualities.P480.value, getQualityFromName("480p SD"))
        assertEquals(Qualities.Unknown.value, getQualityFromName(null))
        assertEquals(Qualities.Unknown.value, getQualityFromName("Auto"))
    }

    @Test
    fun testInferType() {
        assertEquals(ExtractorLinkType.VIDEO, INFER_TYPE)
    }

    @Test
    fun testPackerUnpacker() {
        val packedScript = """eval(function(p,a,c,k,e,d){return p;}('var file = "http://domain.com/0/1.2";', 3, 3, 'stream|video|m3u8'.split('|'), 0, {}))"""
        val extracted = getPacked("<html><script>$packedScript</script></html>")
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("stream|video|m3u8"))

        val unpacked = getAndUnpack(packedScript)
        assertTrue("Unpacked should contain stream", unpacked.contains("stream"))
        assertTrue("Unpacked should contain video", unpacked.contains("video"))
        assertTrue("Unpacked should contain m3u8", unpacked.contains("m3u8"))
    }

    @Test
    fun testNewExtractorLink() = runBlocking {
        val link = newExtractorLink(
            source = "TestExtractor",
            name = "Test Server",
            url = "https://cdn.test/video.m3u8",
            type = ExtractorLinkType.M3U8
        ) {
            this.quality = Qualities.P1080.value
            this.referer = "https://test.com"
        }

        assertEquals("TestExtractor", link.source)
        assertEquals("Test Server", link.name)
        assertEquals("https://cdn.test/video.m3u8", link.url)
        assertEquals(ExtractorLinkType.M3U8, link.type)
        assertEquals(Qualities.P1080.value, link.quality)
        assertEquals("https://test.com", link.referer)
    }

    @Test
    fun testLoadExtractorDispatch() = runBlocking {
        var called = false
        val dummyExtractor = object : ExtractorApi() {
            override val name = "TestExtractor"
            override val mainUrl = "teststream.com"
            override suspend fun getUrl(
                url: String,
                referer: String?,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
            ) {
                called = true
                callback(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = "https://direct.teststream.com/stream.mp4",
                        referer = referer ?: "",
                        quality = Qualities.P1080.value
                    )
                )
            }
        }

        APIHolder.addExtractor(dummyExtractor)

        val receivedLinks = mutableListOf<ExtractorLink>()
        val handled = loadExtractor(
            url = "https://teststream.com/embed/12345",
            referer = "https://test.com"
        ) { link ->
            receivedLinks.add(link)
        }

        assertTrue("loadExtractor should return true for matched domain", handled)
        assertTrue("Extractor getUrl should be called", called)
        assertEquals(1, receivedLinks.size)
        assertEquals("https://direct.teststream.com/stream.mp4", receivedLinks[0].url)
    }

    @Test
    fun testDefaultExtractorsRegistration() {
        val extractors = APIHolder.extractorApis
        println("APIHolder extractors count = ${extractors.size}")
        assertTrue("APIHolder should have registered default extractors, found: ${extractors.size}", extractors.size > 20)
        assertNotNull("StreamWish should be registered", extractors.find { it.name.contains("StreamWish", ignoreCase = true) })
        assertNotNull("FileMoon should be registered", extractors.find { it.name.contains("FileMoon", ignoreCase = true) })
        assertNotNull("StreamTape should be registered", extractors.find { it.name.contains("StreamTape", ignoreCase = true) })
        assertNotNull("MixDrop should be registered", extractors.find { it.name.contains("MixDrop", ignoreCase = true) })
        assertNotNull("DoodStream should be registered", extractors.find { it.name.contains("DoodStream", ignoreCase = true) })
        assertNotNull("Voe should be registered", extractors.find { it.name.contains("Voe", ignoreCase = true) })
        assertNotNull("Rabbitstream should be registered", extractors.find { it.name.contains("Rabbitstream", ignoreCase = true) })
        assertNotNull("VidSrc should be registered", extractors.find { it.name.contains("VidSrc", ignoreCase = true) })
        assertNotNull("VidHide should be registered", extractors.find { it.name.contains("VidHide", ignoreCase = true) })
        assertNotNull("OkRu should be registered", extractors.find { it.name.contains("OkRu", ignoreCase = true) })
    }
}

package com.lagradost.cloudstream3

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lagradost.cloudstream3.extractors.helper.KevsResolver
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.AudioFile
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.DashHelper
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.MpdHelper
import com.lagradost.cloudstream3.utils.MpdManifestParser
import com.lagradost.cloudstream3.utils.PlaylistUtils
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleUtils
import com.lagradost.cloudstream3.utils.videoskip.AnimeSkip
import com.lagradost.cloudstream3.utils.videoskip.AnimeSkipAuth
import com.lagradost.cloudstream3.utils.videoskip.SkipStamp
import com.lagradost.cloudstream3.utils.videoskip.SkipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CloudStreamDependenciesCompatibilityTest {

    @Test
    fun testMpdManifestParserAndDashHelper() {
        val sampleMpd = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" minBufferTime="PT1.5S" type="static">
              <Period id="p0" duration="PT10M">
                <BaseURL>https://stream.example.com/dash/</BaseURL>
                <AdaptationSet id="v0" mimeType="video/mp4" contentType="video">
                  <Representation id="v1080" bandwidth="4500000" width="1920" height="1080">
                    <BaseURL>video_1080.mp4</BaseURL>
                  </Representation>
                  <Representation id="v720" bandwidth="2200000" width="1280" height="720">
                    <BaseURL>video_720.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet id="a0" mimeType="audio/mp4" contentType="audio" lang="en">
                  <Representation id="a_en" bandwidth="128000">
                    <BaseURL>audio_en.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet id="s0" mimeType="text/vtt" contentType="text" lang="en">
                  <Representation id="sub_en">
                    <BaseURL>sub_en.vtt</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = MpdManifestParser.parse(sampleMpd, "https://stream.example.com/dash/manifest.mpd")
        assertEquals("https://stream.example.com/dash/", manifest.periods[0].baseUrl)
        assertEquals(1, manifest.periods.size)
        assertEquals(3, manifest.periods[0].adaptationSets.size)

        var subtitleReceived = false
        val links = DashHelper.parseMpdToLinks(
            source = "TestDash",
            mpdUrl = "https://stream.example.com/dash/manifest.mpd",
            xmlContent = sampleMpd,
            referer = "https://example.com",
            name = "Test Stream",
            subtitleCallback = {
                subtitleReceived = true
                assertEquals("en", it.lang)
                assertEquals("https://stream.example.com/dash/sub_en.vtt", it.url)
            }
        )

        assertTrue("Subtitle callback should be invoked", subtitleReceived)
        assertTrue("Links should not be empty", links.isNotEmpty())
        assertEquals(2, links.size)
        assertEquals(Qualities.P1080.value, links[0].quality)
        assertEquals(1, links[0].audioTracks.size)
        assertEquals("https://stream.example.com/dash/audio_en.mp4", links[0].audioTracks[0].url)
    }

    @Test
    fun testDdosGuardKillerCookieMap() {
        DdosGuardKiller.savedCookiesMap["example.com"] = mapOf("__ddg1_" to "token123")
        assertEquals("token123", DdosGuardKiller.savedCookiesMap["example.com"]?.get("__ddg1_"))
        val killer = DdosGuardKiller(alwaysBypass = false)
        assertNotNull(killer)
    }

    @Test
    fun testKevsResolver() {
        val script = """
            var auth_token = "secure_token_abc";
            var stream_url = "https://cdn.example.com/video.m3u8";
        """.trimIndent()

        val tokens = KevsResolver.extractTokens(script)
        assertTrue(tokens.containsKey("auth_token"))
        assertEquals("secure_token_abc", tokens["auth_token"])

        val unpacked = KevsResolver.unpackAndExtract("console.log('test');")
        assertEquals("console.log('test');", unpacked)
    }

    @Test
    fun testM3u8AudioAndSubtitleParsing() {
        val sampleM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="en",URI="audio_en.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English CC",DEFAULT=NO,FORCED=NO,LANGUAGE="en",URI="subs_en.vtt"
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,AUDIO="audio"
            video_1080.m3u8
        """.trimIndent()

        val extractedSubs = mutableListOf<SubtitleFile>()
        val extractedAudio = mutableListOf<AudioFile>()

        val links = PlaylistUtils.parseM3u8Content(
            source = "TestHLS",
            m3u8Url = "https://hls.example.com/master.m3u8",
            content = sampleM3u8,
            subtitleCallback = { extractedSubs.add(it) },
            audioCallback = { extractedAudio.add(it) }
        )

        assertEquals(1, extractedSubs.size)
        assertEquals("English CC", extractedSubs[0].lang)
        assertEquals("https://hls.example.com/subs_en.vtt", extractedSubs[0].url)

        assertEquals(1, extractedAudio.size)
        assertEquals("English", extractedAudio[0].lang)
        assertEquals("https://hls.example.com/audio_en.m3u8", extractedAudio[0].url)

        assertEquals(1, links.size)
        assertEquals(Qualities.P1080.value, links[0].quality)
        assertEquals(1, links[0].audioTracks.size)
        assertEquals(ExtractorLinkType.M3U8, links[0].type)
    }

    @Test
    fun testSubtitleUtilsCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.cacheDir, "test_subs").apply { mkdirs() }
        val subFile = File(testDir, "BigBuckBunny.1080p.en.srt").apply { writeText("1\n00:00:01 --> 00:00:02\nHello") }
        val movieFile = File(testDir, "BigBuckBunny.1080p.mp4").apply { writeText("dummy") }

        assertTrue(SubtitleUtils.isMatchingSubtitle(subFile.name, movieFile.name, SubtitleUtils.cleanDisplayName(movieFile.name)))

        SubtitleUtils.deleteMatchingSubtitles(testDir, movieFile.name)
        assertFalse(subFile.exists())
        assertTrue(movieFile.exists())

        testDir.deleteRecursively()
    }

    @Test
    fun testAnimeSkipModels() {
        val stamp = SkipStamp(
            type = SkipType.Intro,
            startMs = 30000L,
            endMs = 120000L,
            label = "Opening Song"
        )
        assertEquals(SkipType.Intro, stamp.type)
        assertEquals(30000L, stamp.startMs)
        assertEquals(120000L, stamp.endMs)
        assertEquals("Opening Song", stamp.label)

        assertEquals("narutoshippuden", AnimeSkip.stripName("Naruto: Shippuden"))
        assertEquals("naruto", AnimeSkip.asciiName("Naruto!"))
    }

    @Test
    fun testBackupUtilsExportImport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("test_pref_key", "test_pref_val").commit()

        val json = BackupUtils.exportBackupJson(context)
        assertTrue(json.contains("test_pref_key"))
        assertTrue(json.contains("test_pref_val"))

        prefs.edit().clear().commit()
        assertEquals(null, prefs.getString("test_pref_key", null))

        val restored = BackupUtils.importBackupJson(context, json)
        assertTrue(restored)
        assertEquals("test_pref_val", prefs.getString("test_pref_key", null))
    }
}

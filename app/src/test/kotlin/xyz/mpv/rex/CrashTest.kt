package xyz.mpv.rex

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrashTest {
    @Before
    fun setUp() {
        ShadowLog.stream = System.out
    }

    @Test
    fun testStartup() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        println("Logs from ShadowLog:")
    }
}

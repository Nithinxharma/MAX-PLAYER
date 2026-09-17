package xyz.mpv.rex

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mpv.rex.MainActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupTest {
    @Test
    fun testStartup() {
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
            assert(controller.get() != null)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}

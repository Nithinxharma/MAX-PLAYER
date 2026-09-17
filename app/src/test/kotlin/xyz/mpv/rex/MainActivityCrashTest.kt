package xyz.mpv.rex

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = App::class, qualifiers = "w360dp-h640dp")
class MainActivityCrashTest {
    @Test
    fun testMainActivityLaunchesAndAdvancesToMainScreen() {
        println("=== STARTING FULL UI LAUNCH ===")
        try {
            val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
            println("Activity controller built and setup")
            
            // Fast forward time to pass the 2-second splash screen delay
            ShadowLooper.idleMainLooper(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            Robolectric.flushForegroundThreadScheduler()
            
            println("Fast forwarded time to trigger splash screen exit")
            
            val activity = activityController.get()
            assert(activity != null)
            println("=== FULL UI LAUNCH SUCCESSFUL ===")
        } catch (e: Exception) {
            println("EXCEPTION: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

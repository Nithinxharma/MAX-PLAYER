package xyz.mpv.rex

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class, qualifiers = "w360dp-h640dp")
class MainActivityVerificationTest {
    @Test
    fun testMainActivityLaunches() {
        println("=== STARTING MAIN ACTIVITY LAUNCH VERIFICATION ===")
        try {
            val activityController = Robolectric.buildActivity(MainActivity::class.java).create().start()
            println("Activity controller built and started")
            val activity = activityController.get()
            assert(activity != null)
            println("=== MAIN ACTIVITY LAUNCH VERIFICATION SUCCESSFUL ===")
        } catch (e: Exception) {
            println("EXCEPTION: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

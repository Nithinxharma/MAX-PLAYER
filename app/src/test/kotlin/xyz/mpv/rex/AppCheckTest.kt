package xyz.mpv.rex

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = App::class)
class AppCheckTest {
    @Test
    fun testApp() {
        println("Application class: " + RuntimeEnvironment.getApplication().javaClass.name)
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        println("MainActivity started successfully")
    }
}

package xyz.mpv.rex

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mpv.rex.ui.splash.SplashScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ComposeStartupTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSplashScreen() {
        composeTestRule.setContent {
            SplashScreen.Content()
        }
        composeTestRule.waitForIdle()
        println("Compose UI rendered successfully")
    }
}

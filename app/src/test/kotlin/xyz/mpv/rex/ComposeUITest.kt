package xyz.mpv.rex

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = App::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeUITest {
    // using v2 compose rule
    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<MainActivity>()

    @Test
    fun testUI() {
        composeTestRule.waitForIdle()
        println("Compose UI Tree:")
        println(composeTestRule.onRoot().printToString())
    }
}

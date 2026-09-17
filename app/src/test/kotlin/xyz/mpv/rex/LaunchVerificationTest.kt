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
class LaunchVerificationTest {
    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyAppStartupAndRendering() {
        println("=== STARTING APP LAUNCH VERIFICATION ===")
        composeTestRule.waitForIdle()
        
        println("=== COMPOSE UI TREE RENDERED ===")
        val tree = composeTestRule.onRoot().printToString()
        println(tree)
        
        assert(tree.isNotEmpty()) { "Compose tree should not be empty!" }
        println("=== LAUNCH VERIFICATION SUCCESSFUL ===")
    }
}

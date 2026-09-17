package xyz.mpv.rex

import org.junit.Test
import androidx.navigation3.runtime.NavBackStack

class NavTest2 {
    @Test
    fun dumpMethods() {
        val methods = NavBackStack::class.java.methods
        for (m in methods) {
            println("METHOD: " + m.name)
        }
    }
}

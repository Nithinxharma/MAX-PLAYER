import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorLink

fun main() {
    val methods = MainAPI::class.java.methods
    methods.forEach { println(it.name + " " + it.parameterTypes.map { it.name }.joinToString()) }
    println("ExtractorLink:")
    ExtractorLink::class.java.methods.forEach { println(it.name) }
}

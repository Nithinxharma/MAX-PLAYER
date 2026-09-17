package android.util
import java.util.Base64 as JavaBase64
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = JavaBase64.getEncoder().encode(input)
    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = JavaBase64.getEncoder().encodeToString(input)
    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = JavaBase64.getDecoder().decode(str)
}

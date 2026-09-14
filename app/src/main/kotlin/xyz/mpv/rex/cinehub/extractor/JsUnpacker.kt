package xyz.mpv.rex.cinehub.extractor

import android.util.Log
import kotlin.math.pow

/**
 * Port of Dean Edwards JavaScript unpacker for packed video player scripts.
 */
class JsUnpacker(private val packedJS: String?) {

    fun detect(): Boolean {
        val js = packedJS?.replace(" ", "") ?: return false
        return Regex("eval\\(function\\(p,a,c,k,e,[rd]").containsMatchIn(js)
    }

    fun unpack(): String? {
        val js = packedJS ?: return null
        try {
            val match = Regex(
                """(?s)\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)"""
            ).find(js)
            if (match != null && match.groupValues.size == 5) {
                val payload = match.groupValues[1].replace("\\'", "'")
                val radixStr = match.groupValues[2]
                val countStr = match.groupValues[3]
                val symtab = match.groupValues[4].split("|").toTypedArray()
                var radix = 36
                var count = 0
                try {
                    radix = radixStr.toIntOrNull() ?: radix
                } catch (_: Exception) {}
                try {
                    count = countStr.toIntOrNull() ?: 0
                } catch (_: Exception) {}
                if (symtab.size != count) {
                    return null
                }
                val unbase = Unbase(radix)
                val wordRegex = Regex("""\b[a-zA-Z0-9_]+\b""")
                val decoded = StringBuilder(payload)
                var replaceOffset = 0
                wordRegex.findAll(payload).forEach { wordMatch ->
                    val word = wordMatch.value
                    val x = unbase.unbase(word)
                    val value = if (x in symtab.indices) symtab[x] else null
                    if (!value.isNullOrEmpty()) {
                        decoded.setRange(
                            wordMatch.range.first + replaceOffset,
                            wordMatch.range.last + 1 + replaceOffset,
                            value
                        )
                        replaceOffset += value.length - word.length
                    }
                }
                return decoded.toString()
            }
        } catch (e: Exception) {
            Log.e("JsUnpacker", "Failed to unpack JS: ${e.message}")
        }
        return null
    }

    private inner class Unbase(private val radix: Int) {
        private val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val ALPHABET_95 =
            " !\"#$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
        private var alphabet: String? = null
        private var dictionary: HashMap<String, Int>? = null

        fun unbase(str: String): Int {
            var ret = 0
            if (alphabet == null) {
                ret = str.toInt(radix)
            } else {
                val tmp = StringBuilder(str).reverse().toString()
                for (i in tmp.indices) {
                    val charStr = tmp.substring(i, i + 1)
                    val dictVal = dictionary?.get(charStr) ?: 0
                    ret += (radix.toDouble().pow(i.toDouble()) * dictVal).toInt()
                }
            }
            return ret
        }

        init {
            if (radix > 36) {
                when {
                    radix < 62 -> alphabet = ALPHABET_62.substring(0, radix)
                    radix in 63..94 -> alphabet = ALPHABET_95.substring(0, radix)
                    radix == 62 -> alphabet = ALPHABET_62
                    radix == 95 -> alphabet = ALPHABET_95
                }
                dictionary = HashMap(95)
                for (i in 0 until (alphabet?.length ?: 0)) {
                    alphabet?.substring(i, i + 1)?.let { dictionary?.put(it, i) }
                }
            }
        }
    }
}

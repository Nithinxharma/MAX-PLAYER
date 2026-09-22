package com.lagradost.cloudstream3.utils

object JsUnpacker {
    fun getPacked(string: String): String? {
        val regex = Regex("""eval\(function\(p,a,c,k,e,[rd]\).*?\.split\(['"]\|['"]\).*?\)\)""", setOf(RegexOption.DOT_MATCHES_ALL))
        return regex.find(string)?.value
    }

    fun unpack(packed: String): String? {
        val regex = Regex("""\}\s*\(\s*(['"])(.*?)\1\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(['"])(.*?)\5\.split\(['"]\|['"]\)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val match = regex.find(packed) ?: return null
        val payload = match.groupValues[2]
        val radix = match.groupValues[3].toIntOrNull() ?: 36
        val keywords = match.groupValues[6].split("|")

        val wordRegex = Regex("""\b\w+\b""")
        return wordRegex.replace(payload) { m ->
            val word = m.value
            val idx = baseDecode(word, radix)
            if (idx != null && idx in keywords.indices && keywords[idx].isNotEmpty()) {
                keywords[idx]
            } else {
                word
            }
        }
    }

    fun unpackAndCombine(script: String): String? {
        val packed = getPacked(script) ?: script
        return unpack(packed) ?: packed
    }

    private fun baseDecode(numStr: String, base: Int): Int? {
        if (base < 2 || base > 62) return null
        var result = 0
        for (ch in numStr) {
            val digit = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'z' -> ch - 'a' + 10
                in 'A'..'Z' -> ch - 'A' + 36
                else -> return null
            }
            if (digit >= base) return null
            result = result * base + digit
        }
        return result
    }
}

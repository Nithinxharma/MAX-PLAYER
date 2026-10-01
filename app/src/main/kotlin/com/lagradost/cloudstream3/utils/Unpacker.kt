package com.lagradost.cloudstream3.utils

import java.util.regex.Pattern

object Unpacker {
    private val PACKED_PATTERN =
        Pattern.compile("""\}\s*\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""")

    fun unpack(packedJs: String): String? {
        val matcher = PACKED_PATTERN.matcher(packedJs)
        if (!matcher.find()) return null

        val payload = matcher.group(1) ?: return null
        val radix = matcher.group(2)?.toIntOrNull() ?: 10
        val count = matcher.group(3)?.toIntOrNull() ?: 0
        val symtab = matcher.group(4)?.split("|") ?: return null

        val unbase = Unbaser(radix)
        val wordPattern = Pattern.compile("""\b\w+\b""")
        val wordMatcher = wordPattern.matcher(payload)
        val sb = StringBuffer()

        while (wordMatcher.find()) {
            val word = wordMatcher.group()
            val index = unbase.unbase(word)
            val replacement = if (index in symtab.indices && symtab[index].isNotEmpty()) {
                symtab[index]
            } else {
                word
            }
            wordMatcher.appendReplacement(sb, MatcherQuoteReplacement(replacement))
        }
        wordMatcher.appendTail(sb)
        return sb.toString()
    }

    private fun MatcherQuoteReplacement(s: String): String {
        return java.util.regex.Matcher.quoteReplacement(s)
    }

    private class Unbaser(val radix: Int) {
        private val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

        fun unbase(str: String): Int {
            if (radix == 10) return str.toIntOrNull() ?: -1
            var res = 0
            for (ch in str) {
                val index = ALPHABET.indexOf(ch)
                if (index < 0 || index >= radix) return -1
                res = res * radix + index
            }
            return res
        }
    }
}

package com.lagradost.cloudstream3.utils

object Levenshtein {
    fun partialRatio(s1: String, s2: String): Int {
        if (s1.isEmpty() || s2.isEmpty()) return 0
        val str1 = s1.lowercase()
        val str2 = s2.lowercase()
        if (str1.contains(str2) || str2.contains(str1)) return 100
        val short = if (str1.length <= str2.length) str1 else str2
        val long = if (str1.length > str2.length) str1 else str2
        var maxRatio = 0
        for (i in 0..(long.length - short.length)) {
            val sub = long.substring(i, i + short.length)
            val dist = distance(short, sub)
            val ratio = ((short.length - dist).toDouble() / short.length * 100).toInt()
            if (ratio > maxRatio) maxRatio = ratio
        }
        return maxRatio
    }

    private fun distance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}

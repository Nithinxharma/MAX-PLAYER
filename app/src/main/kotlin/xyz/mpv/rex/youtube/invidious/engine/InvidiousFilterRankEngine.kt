package xyz.mpv.rex.youtube.invidious.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import xyz.mpv.rex.youtube.invidious.model.InvidiousInstance

/**
 * Filter & Rank Engine for Invidious Instances.
 *
 * Parses the raw Invidious API registry JSON:
 * `[ [ "domain.com", { "type": "https", "api": true, "monitor": { "dailyRatios": [ { "ratio": "98.500" } ] } } ], ... ]`
 *
 * Filters instances strictly by:
 * 1. type == "https"
 * 2. api == true
 * 3. monitor.dailyRatios[0].ratio > 95.0 (handling all possible variations safely)
 *
 * Ranks instances primarily by highest health ratio and secondarily by lowest latency.
 */
object InvidiousFilterRankEngine {
    private const val TAG = "InvidiousRankEngine"
    private const val MIN_HEALTH_RATIO = 95.0

    /**
     * Parses, filters, and ranks the raw instances JSON string.
     *
     * @param rawJson Raw string received from https://api.invidious.io/instances.json
     * @return Ranked list of healthy Invidious instances
     */
    fun parseAndRank(rawJson: String): List<InvidiousInstance> {
        if (rawJson.isBlank()) return emptyList()

        val parsedInstances = mutableListOf<InvidiousInstance>()

        try {
            val rootArray = JSONArray(rawJson)
            for (i in 0 until rootArray.length()) {
                val item = rootArray.optJSONArray(i) ?: continue
                if (item.length() < 2) continue

                val domain = item.optString(0, "").trim()
                val config = item.optJSONObject(1) ?: continue

                if (domain.isBlank()) continue

                // 1. Strict Condition: type == "https"
                val type = config.optString("type", "").lowercase().trim()
                if (type != "https") continue

                // 2. Strict Condition: api == true
                val hasApi = config.optBoolean("api", false)
                if (!hasApi) continue

                // 3. Strict Condition: monitor.dailyRatios[0].ratio > 95.0
                val healthRatio = extractFirstDailyRatio(config)
                if (healthRatio == null || healthRatio <= MIN_HEALTH_RATIO) continue

                // Optional latency detection
                val latencyMs = extractLatency(config)
                val flag = config.optString("flag", "").takeIf { it.isNotBlank() }
                val region = config.optString("region", "").takeIf { it.isNotBlank() }
                val customUri = config.optString("uri", "").trim()
                val baseUrl = if (customUri.startsWith("http://") || customUri.startsWith("https://")) {
                    customUri.trimEnd('/')
                } else {
                    "https://$domain"
                }

                parsedInstances.add(
                    InvidiousInstance(
                        domain = domain,
                        baseUrl = baseUrl,
                        type = type,
                        hasApi = true,
                        healthRatio = healthRatio,
                        latencyMs = latencyMs,
                        flag = flag,
                        region = region,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Invidious instances JSON: ${e.message}", e)
        }

        // Rank by highest health ratio first; if ratio difference is negligible (within 0.5%),
        // prioritize lower latency servers.
        return parsedInstances.sortedWith(
            Comparator { a, b ->
                val ratioDiff = b.healthRatio.compareTo(a.healthRatio)
                if (ratioDiff != 0 && Math.abs(b.healthRatio - a.healthRatio) > 0.5) {
                    ratioDiff
                } else {
                    val latA = a.latencyMs ?: Long.MAX_VALUE
                    val latB = b.latencyMs ?: Long.MAX_VALUE
                    latA.compareTo(latB)
                }
            }
        )
    }

    /**
     * Safely navigates config -> monitor -> dailyRatios[0] -> ratio
     * Supports ratio values given as Double, Long, or String (e.g. "98.500" or 98.5).
     */
    private fun extractFirstDailyRatio(config: JSONObject): Double? {
        val monitor = config.optJSONObject("monitor") ?: return null
        val dailyRatios = monitor.optJSONArray("dailyRatios") ?: return null
        if (dailyRatios.length() == 0) return null

        val firstEntry = dailyRatios.optJSONObject(0) ?: return null
        val rawRatio = firstEntry.opt("ratio") ?: return null

        return when (rawRatio) {
            is Number -> rawRatio.toDouble()
            is String -> rawRatio.toDoubleOrNull()
            else -> null
        }
    }

    /**
     * Extracts latency in milliseconds if reported by monitor or stats.
     */
    private fun extractLatency(config: JSONObject): Long? {
        val monitor = config.optJSONObject("monitor")
        val responseTime = monitor?.optDouble("responseTime")?.takeIf { !it.isNaN() && it > 0 }
        if (responseTime != null) return responseTime.toLong()

        val stats = config.optJSONObject("stats")
        val ping = stats?.optDouble("ping")?.takeIf { !it.isNaN() && it > 0 }
        if (ping != null) return ping.toLong()

        return null
    }
}

package xyz.mpv.rex.youtube.invidious.model

import kotlinx.serialization.Serializable

/**
 * Representation of an Invidious server instance.
 *
 * @property domain Domain name of the instance (e.g. "invidious.projectsegfau.lt")
 * @property baseUrl Full HTTPS URL base of the instance (e.g. "https://invidious.projectsegfau.lt")
 * @property type Protocol type ("https" or "http")
 * @property hasApi Whether the Invidious REST API is enabled
 * @property healthRatio Uptime / health ratio (0.0 to 100.0) from the monitor
 * @property latencyMs Estimated or reported latency in milliseconds, if available
 * @property region Geographic region / country code
 * @property flag Country emoji flag
 */
@Serializable
data class InvidiousInstance(
    val domain: String,
    val baseUrl: String,
    val type: String,
    val hasApi: Boolean,
    val healthRatio: Double,
    val latencyMs: Long? = null,
    val region: String? = null,
    val flag: String? = null,
)

package xyz.mpv.rex.tv.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object HotspotNetworkHelper {

    data class NetworkInfo(
        val ipAddress: String,
        val isHotspot: Boolean,
        val isWifi: Boolean,
        val networkName: String,
        val port: Int = 8765
    ) {
        val tvWebUrl: String
            get() = "http://$ipAddress:$port/tv"

        val directStreamUrl: String
            get() = "http://$ipAddress:$port/stream"
    }

    /**
     * Finds the best active local IP address for TV streaming.
     * Prioritizes Hotspot (e.g. ap0, wlan1, rndis, 192.168.43.1), then Wi-Fi (wlan0), then loopback.
     */
    fun getLocalNetworkInfo(context: Context, port: Int = 8765): NetworkInfo {
        var hotspotIp: String? = null
        var wifiIp: String? = null
        var fallbackIp: String? = null

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue

                val name = intf.name.lowercase()
                val addrs = Collections.list(intf.inetAddresses)

                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue

                        // Standard Android Wi-Fi Tethering / Hotspot subnets
                        if (name.contains("ap") || name.contains("tether") || name.contains("rndis") || host.startsWith("192.168.43.")) {
                            hotspotIp = host
                            break
                        } else if (name.contains("wlan") || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            if (wifiIp == null) wifiIp = host
                        } else {
                            if (fallbackIp == null) fallbackIp = host
                        }
                    }
                }
                if (hotspotIp != null) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try getting Wi-Fi IP via WifiManager as fallback
        if (wifiIp == null && hotspotIp == null) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    wifiIp = Formatter.formatIpAddress(ipInt)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val chosenIp = hotspotIp ?: wifiIp ?: fallbackIp ?: "127.0.0.1"
        val isHotspot = hotspotIp != null || chosenIp.startsWith("192.168.43.")
        val isWifi = !isHotspot && chosenIp != "127.0.0.1"

        val label = when {
            isHotspot -> "Phone Mobile Hotspot"
            isWifi -> "Local Wi-Fi Network"
            else -> "Local Device"
        }

        return NetworkInfo(
            ipAddress = chosenIp,
            isHotspot = isHotspot,
            isWifi = isWifi,
            networkName = label,
            port = port
        )
    }
}

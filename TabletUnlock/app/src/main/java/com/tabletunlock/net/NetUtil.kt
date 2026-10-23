package com.tabletunlock.net

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    /** أول عنوان IPv4 محلي (192.168.x / 10.x …) على الواي فاي. */
    fun localIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}

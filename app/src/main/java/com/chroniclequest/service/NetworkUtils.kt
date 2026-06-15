package com.chroniclequest.service

import java.net.Inet4Address
import java.net.NetworkInterface

/** Best-effort local Wi-Fi IPv4 address for showing the web-monitor URL. */
object NetworkUtils {
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}

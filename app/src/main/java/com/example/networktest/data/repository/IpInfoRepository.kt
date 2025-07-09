package com.example.networktest.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import com.example.networktest.data.model.IpInformation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader


class IpInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Suppress("DEPRECATION") // Required for WifiManager on older APIs and Formatter.formatIpAddress
    fun getIpInformation(): Flow<IpInformation> = flow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        var localIpAddress: String? = null
        var gateway: String? = null
        var subnetMask: String? = null
        var dnsServers: List<String>? = null
        var leaseDuration: Int? = null
        var ssid: String? = null
        var bssid: String? = null
        var linkSpeed: Int? = null
        var signalStrength: Int? = null
        var interfaceName: String? = null
        var connectionType: String? = "N/A"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

            if (activeNetwork != null && networkCapabilities != null && linkProperties != null) {
                interfaceName = linkProperties.interfaceName

                // Determine Connection Type
                when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        connectionType = "Wi-Fi"
                        val wifiInfo = wifiManager.connectionInfo
                        if (wifiInfo != null) {
                            ssid = wifiInfo.ssid.removePrefix("\"").removeSuffix("\"")
                            bssid = wifiInfo.bssid
                            linkSpeed = wifiInfo.linkSpeed // Mbps
                            signalStrength = wifiInfo.rssi
                        }
                    }
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        connectionType = "Mobile Data"
                    }
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        connectionType = "Ethernet"
                    }
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                        connectionType = "VPN"
                    }
                    else -> {
                        connectionType = "Other"
                    }
                }


                // Get IP Address
                linkProperties.linkAddresses.find { it.address is Inet4Address }?.let {
                    localIpAddress = it.address.hostAddress
                    // Calculate Subnet Mask from Prefix Length
                    val prefixLength = it.prefixLength
                    val mask = (-1 shl (32 - prefixLength))
                    val bytes = byteArrayOf(
                        (mask shr 24 and 0xFF).toByte(),
                        (mask shr 16 and 0xFF).toByte(),
                        (mask shr 8 and 0xFF).toByte(),
                        (mask and 0xFF).toByte()
                    )
                    try {
                        subnetMask = java.net.InetAddress.getByAddress(bytes).hostAddress
                    } catch (e: Exception) { /* Ignore */ }
                }

                // Get Gateway
                linkProperties.routes.find { it.isDefaultRoute }?.gateway?.hostAddress?.let {
                    gateway = it
                }

                // Get DNS Servers
                dnsServers = linkProperties.dnsServers.mapNotNull { it.hostAddress }

                // DHCP Info (Lease Duration) - This is often not directly available via LinkProperties
                // For Wi-Fi, it might be in wifiManager.dhcpInfo but that's also limited.
                // Lease duration is more reliably obtained if you implement a DHCP client, which is complex.
                // We'll try with WifiManager's DHCP info if it's a Wi-Fi connection.
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val dhcpInfo = wifiManager.dhcpInfo
                    if (dhcpInfo != null) {
                        leaseDuration = dhcpInfo.leaseDuration
                        // Note: dhcpInfo.ipAddress, gateway, dns1, dns2 are often more reliable for Wi-Fi pre-M
                        // but LinkProperties is the preferred source on M+
                        if (localIpAddress == null || localIpAddress == "0.0.0.0") {
                             localIpAddress = Formatter.formatIpAddress(dhcpInfo.ipAddress)
                        }
                         if (gateway == null || gateway == "0.0.0.0") {
                             gateway = Formatter.formatIpAddress(dhcpInfo.gateway)
                        }
                        if (dnsServers.isNullOrEmpty()) {
                            val tempDns = mutableListOf<String>()
                            if (dhcpInfo.dns1 != 0) tempDns.add(Formatter.formatIpAddress(dhcpInfo.dns1))
                            if (dhcpInfo.dns2 != 0) tempDns.add(Formatter.formatIpAddress(dhcpInfo.dns2))
                            if (tempDns.isNotEmpty()) dnsServers = tempDns
                        }
                         if (subnetMask == null || subnetMask == "0.0.0.0" && dhcpInfo.netmask !=0) {
                            subnetMask = Formatter.formatIpAddress(dhcpInfo.netmask)
                        }
                    }
                }

            }
        } else { // Fallback for older Android versions (Pre-M)
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                interfaceName = activeNetworkInfo.typeName // Less specific than LinkProperties.interfaceName

                when (activeNetworkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> {
                        connectionType = "Wi-Fi"
                        val wifiInfo = wifiManager.connectionInfo
                        val dhcpInfo = wifiManager.dhcpInfo
                        if (wifiInfo != null) {
                            localIpAddress = Formatter.formatIpAddress(wifiInfo.ipAddress) // Deprecated but necessary
                            ssid = wifiInfo.ssid.removePrefix("\"").removeSuffix("\"")
                            bssid = wifiInfo.bssid
                            linkSpeed = wifiInfo.linkSpeed
                            signalStrength = wifiInfo.rssi
                        }
                        if (dhcpInfo != null) {
                            gateway = Formatter.formatIpAddress(dhcpInfo.gateway)
                            dnsServers = listOfNotNull(
                                Formatter.formatIpAddress(dhcpInfo.dns1).takeIf { it != "0.0.0.0" },
                                Formatter.formatIpAddress(dhcpInfo.dns2).takeIf { it != "0.0.0.0" }
                            )
                            leaseDuration = dhcpInfo.leaseDuration
                            subnetMask = Formatter.formatIpAddress(dhcpInfo.netmask)
                        }
                    }
                    ConnectivityManager.TYPE_MOBILE -> {
                        connectionType = "Mobile Data"
                        // Getting IP for mobile data pre-M is tricky and often requires iterating NetworkInterfaces
                        localIpAddress = getMobileIpAddress()
                        // Gateway, DNS for mobile data are harder to get reliably pre-M without root or specific APIs.
                    }
                    ConnectivityManager.TYPE_ETHERNET -> {
                        connectionType = "Ethernet"
                        // Similar to mobile, may need NetworkInterface iteration
                        localIpAddress = getEthernetIpAddress()
                    }
                    else -> {
                        connectionType = activeNetworkInfo.typeName
                    }
                }
            }
        }

        // Ensure "0.0.0.0" is treated as null for consistency
        if (localIpAddress == "0.0.0.0") localIpAddress = null
        if (gateway == "0.0.0.0") gateway = null
        if (subnetMask == "0.0.0.0") subnetMask = null
        dnsServers = dnsServers?.filter { it != "0.0.0.0" }
        if (dnsServers?.isEmpty() == true) dnsServers = null


        emit(
            IpInformation(
                localIpAddress = localIpAddress,
                gateway = gateway,
                subnetMask = subnetMask,
                dnsServers = dnsServers,
                leaseDuration = leaseDuration,
                ssid = ssid,
                bssid = bssid,
                linkSpeed = linkSpeed,
                signalStrength = signalStrength,
                interfaceName = interfaceName,
                connectionType = connectionType
            )
        )
    }.flowOn(Dispatchers.IO)


    // Helper for Pre-M mobile IP, less reliable
    private fun getMobileIpAddress(): String? {
        try {
            val networkInterfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in networkInterfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                // Typically mobile interfaces might have names like "rmnet" or "pdp"
                if (intf.displayName.contains("mobile", true) || intf.displayName.contains("rmnet", true) || intf.displayName.contains("pdp", true) ) {
                    val addresses = java.util.Collections.list(intf.inetAddresses)
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (ex: Exception) { /* log or ignore */ }
        return null
    }

    private fun getEthernetIpAddress(): String? {
         try {
            val networkInterfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in networkInterfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                 // Typically ethernet interfaces might have names like "eth"
                if (intf.displayName.contains("eth", true)) {
                    val addresses = java.util.Collections.list(intf.inetAddresses)
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (ex: Exception) { /* log or ignore */ }
        return null
    }


    fun fetchPublicIpAddress(): Flow<String?> = flow {
        // Try a few reliable services, first one wins.
        // These services might change, so error handling is important.
        val ipServices = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://checkip.amazonaws.com",
            "https://ipinfo.io/ip"
        )

        var publicIp: String? = null
        for (serviceUrl in ipServices) {
            try {
                val url = URL(serviceUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000 // 3 seconds
                connection.readTimeout = 3000    // 3 seconds

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val ip = reader.readLine()?.trim()
                    reader.close()
                    if (ip != null && isValidIpAddress(ip)) {
                        publicIp = ip
                        break // Found a valid IP
                    }
                }
            } catch (e: Exception) {
                // Try next service
            }
        }
        emit(publicIp)
    }.flowOn(Dispatchers.IO)

    private fun isValidIpAddress(ip: String): Boolean {
        // Simple regex for IPv4, can be expanded for IPv6 if needed
        return ip.matches(Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"))
    }
}

package com.example.networktest.data.model

data class IpInformation(
    val localIpAddress: String?,
    val publicIpAddress: String? = null, // To be fetched separately if needed
    val gateway: String?,
    val subnetMask: String?,
    val dnsServers: List<String>?,
    val leaseDuration: Int? = null, // If available (DHCP info)
    val ssid: String? = null, // If WiFi connection
    val bssid: String? = null, // If WiFi connection
    val linkSpeed: Int? = null, // If WiFi connection (Mbps)
    val signalStrength: Int? = null, // If WiFi connection (RSSI or level)
    val interfaceName: String? = null,
    val connectionType: String? // e.g., Wi-Fi, Ethernet, Mobile
)

package com.example.networktest.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Ping : Screen("ping", "Ping", Icons.Filled.NetworkPing, Icons.Outlined.NetworkPing)
    object Traceroute : Screen("traceroute", "Traceroute", Icons.Filled.Route, Icons.Outlined.Route)
    object PortScan : Screen("portscan", "Port Scan", Icons.Filled.Radar, Icons.Outlined.Radar)
    object IpInfo : Screen("ipinfo", "IP Info", Icons.Filled.Info, Icons.Outlined.Info)
    object WifiAnalyzer : Screen("wifianalyzer", "Wi-Fi Analyzer", Icons.Filled.Wifi, Icons.Outlined.Wifi)
    object DnsLookup : Screen("dnslookup", "DNS Lookup", Icons.Filled.Dns, Icons.Outlined.Dns)
    object SpeedTest : Screen("speedtest", "Speed Test", Icons.Filled.Speed, Icons.Outlined.Speed)
    // Could add more like settings, about, etc.
    // object NetworkInterfaces : Screen("netinterfaces", "Interfaces", Icons.Filled.SettingsEthernet, Icons.Outlined.SettingsEthernet)
}

val bottomNavItems = listOf(
    Screen.IpInfo,
    Screen.Ping,
    Screen.WifiAnalyzer,
    Screen.PortScan,
    // Add more primary screens here, others can be in a "More" menu or drawer if too many
)

// A secondary list for a potential "More" screen or drawer if not all fit in bottom nav
val allAppScreens = listOf(
    Screen.IpInfo,
    Screen.Ping,
    Screen.Traceroute,
    Screen.PortScan,
    Screen.WifiAnalyzer,
    Screen.DnsLookup,
    Screen.SpeedTest,
    // Screen.NetworkInterfaces
)

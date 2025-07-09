package com.example.networktest.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.networktest.data.model.WifiNetwork
import com.example.networktest.ui.viewmodel.WifiAnalyzerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WifiAnalyzerScreen(viewModel: WifiAnalyzerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permissions handling
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            // CHANGE_WIFI_STATE is also needed by repository but often granted if app targets older SDK or system app
        )
    )

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.onPermissionsGranted()
        } else {
            if (locationPermissions.shouldShowRationale || !locationPermissions.permissionRequested) {
                // viewModel.onPermissionsDenied() // Show initial message or if rationale needed
            } else {
                 viewModel.onPermissionsDenied() // Permissions were denied and rationale not needed
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Available Wi-Fi Networks", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                if (locationPermissions.allPermissionsGranted) {
                    viewModel.refreshWifiNetworks()
                } else {
                    locationPermissions.launchMultiplePermissionRequest()
                }
            }, enabled = !uiState.isScanning) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.missingPermissions) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Permissions Required", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This feature requires Location and Wi-Fi permissions to scan for networks. " +
                            "Please grant the permissions to continue.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { locationPermissions.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
                 uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=8.dp))
                }
            }
        } else if (uiState.isLoading && uiState.wifiNetworks.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (uiState.isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Scanning for networks...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (uiState.wifiNetworks.isEmpty() && uiState.errorMessage == null && !uiState.isLoading) {
            Text(
                "No Wi-Fi networks found, or Wi-Fi might be disabled. Try refreshing.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
            )
        }


        uiState.errorMessage?.let {
            if(!uiState.missingPermissions){ // Don't show general error if permission message is shown
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                )
            }
        }


        if (uiState.wifiNetworks.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.wifiNetworks, key = { it.bssid }) { network ->
                    WifiNetworkItem(network)
                    Divider()
                }
            }
        }
    }
}

@Composable
fun WifiNetworkItem(network: WifiNetwork) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WifiSignalIcon(signalLevel = network.signalLevel, isConnected = network.isConnected)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = network.ssid.ifEmpty { "(Hidden SSID)" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (network.isConnected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = network.bssid,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${network.signalStrengthDbm} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = signalColor(network.signalStrengthDbm)
            )
            Text(
                "Ch: ${network.channel} (${network.frequency} MHz)",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (network.securityType != "Open") {
                    Icon(
                        Icons.Default.WifiLock,
                        contentDescription = "Secured",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    network.securityType,
                    style = MaterialTheme.typography.bodySmall
                )
            }

        }
    }
}

@Composable
fun WifiSignalIcon(signalLevel: Int, isConnected: Boolean) {
    val icon = when (signalLevel) {
        0 -> Icons.Filled.SignalWifi0Bar
        1 -> Icons.Filled.SignalWifiBad // Represents 1 bar or weak signal
        2 -> Icons.Filled.SignalWifi4Bar // Represents 2 bars (using 4 bar icon and tinting it)
        3 -> Icons.Filled.SignalWifi4Bar // Represents 3 bars
        else -> Icons.Filled.SignalWifi4Bar // 4 or more bars (Strongest)
    }
    val color = if (isConnected) MaterialTheme.colorScheme.primary else
        when (signalLevel) {
            0 -> MaterialTheme.colorScheme.error
            1 -> Color.Yellow.copy(red= 0.8f, green = 0.6f) // Darker Yellow/Orange for weak
            2 -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary // Strong signal
        }
    Icon(icon, contentDescription = "Signal Strength: $signalLevel", tint = color, modifier = Modifier.size(28.dp))
}

@Composable
fun signalColor(rssi: Int): Color {
    return when {
        rssi >= -55 -> Color(0xFF008000) // Green for Excellent
        rssi >= -65 -> Color(0xFF8FBC8F) // Light Green for Good
        rssi >= -75 -> Color(0xFFFFA500) // Orange for Fair
        rssi >= -85 -> Color(0xFFFF4500) // Red-Orange for Weak
        else -> Color.Red // Red for Very Weak
    }
}

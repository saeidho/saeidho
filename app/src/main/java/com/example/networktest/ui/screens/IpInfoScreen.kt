package com.example.networktest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.networktest.data.model.IpInformation
import com.example.networktest.ui.viewmodel.IpInfoViewModel

@Composable
fun IpInfoScreen(viewModel: IpInfoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { viewModel.loadIpInformation() }, enabled = !uiState.isLoading) {
            Text("Refresh IP Information")
        }

        if (uiState.isLoading && uiState.localIpInfo == null && uiState.publicIpAddress == null) {
            CircularProgressIndicator()
        } else {
            uiState.localIpInfo?.let { DisplayIpInformation(it) }
            if (uiState.publicIpAddress == null && uiState.localIpInfo?.publicIpAddress == null && !uiState.isLoadingPublicIp) {
                 Text("Public IP: Not available", style = MaterialTheme.typography.bodyLarge)
            }
        }

        uiState.errorMessage?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun DisplayIpInformation(ipInfo: IpInformation) {
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("IP Information", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        ipInfo.connectionType?.let { Text("Connection Type: $it", style = MaterialTheme.typography.bodyLarge) }
        ipInfo.interfaceName?.let { Text("Interface: $it", style = MaterialTheme.typography.bodyLarge) }
        ipInfo.localIpAddress?.let { Text("Local IP: $it", style = MaterialTheme.typography.bodyLarge) }
        ipInfo.publicIpAddress?.let { Text("Public IP: $it", style = MaterialTheme.typography.bodyLarge) }
        ipInfo.gateway?.let { Text("Gateway: $it", style = MaterialTheme.typography.bodyLarge) }
        ipInfo.subnetMask?.let { Text("Subnet Mask: $it", style = MaterialTheme.typography.bodyLarge) }
        ipInfo.dnsServers?.let {
            if (it.isNotEmpty()) Text("DNS Servers: ${it.joinToString()}", style = MaterialTheme.typography.bodyLarge)
        }
        ipInfo.leaseDuration?.let { if(it > 0) Text("Lease Duration: $it seconds", style = MaterialTheme.typography.bodyLarge) }

        if (ipInfo.connectionType == "Wi-Fi") {
            Text("Wi-Fi Details:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            ipInfo.ssid?.let { Text("  SSID: $it", style = MaterialTheme.typography.bodyMedium) }
            ipInfo.bssid?.let { Text("  BSSID: $it", style = MaterialTheme.typography.bodyMedium) }
            ipInfo.linkSpeed?.let { Text("  Link Speed: $it Mbps", style = MaterialTheme.typography.bodyMedium) }
            ipInfo.signalStrength?.let { Text("  Signal Strength: $it dBm", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

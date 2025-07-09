package com.example.networktest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.networktest.data.model.PingResult
import com.example.networktest.ui.viewmodel.PingViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(viewModel: PingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.hostInput,
            onValueChange = { viewModel.onHostInputChange(it) },
            label = { Text("Host or IP Address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (uiState.canStartPing) viewModel.startPing(uiState.hostInput)
            }),
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.errorMessage?.contains("Host", ignoreCase = true) == true ||
                      uiState.errorMessage?.contains("Invalid", ignoreCase = true) == true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.startPing(uiState.hostInput)
                },
                enabled = uiState.canStartPing && uiState.hostInput.isNotBlank()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Ping")
                Spacer(Modifier.width(4.dp))
                Text("Start Ping")
            }
            Button(
                onClick = { viewModel.cancelPing() },
                enabled = uiState.isLoading
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Ping")
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }

        if (uiState.isLoading && uiState.pingResults.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        uiState.errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.pingResults) { result ->
                PingResultItem(result)
                Divider()
            }
        }

        uiState.summaryResult?.let { summary ->
            Divider(thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp))
            Text("Summary:", style = MaterialTheme.typography.titleMedium)
            PingSummaryItem(summary)
        }
    }
}

@Composable
fun PingResultItem(result: PingResult) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        if (result.rawOutput.startsWith("PING ") || result.rawOutput.startsWith("Pinging ")) {
            Text(result.rawOutput.trim(), style = MaterialTheme.typography.bodyMedium)
        } else if (result.isSuccess && result.timeTakenMs != null) {
            val ttlText = result.ttl?.let { "ttl=$it" } ?: ""
            val ipText = result.ipAddress?.let { "from $it" } ?: result.rawOutput.substringBefore(":").substringAfter("from ")
            Text(
                String.format(Locale.US,"Reply %s: bytes=%d time=%.2fms %s",
                    ipText,
                    64, // Assuming 64 bytes for now, can parse if available
                    result.timeTakenMs,
                    ttlText
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (result.error != null) {
            Text(result.rawOutput.trim(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Text(result.rawOutput.trim(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PingSummaryItem(summary: PingResult) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        summary.ipAddress?.let { Text("Target IP: $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            "Packets: Transmitted = ${summary.packetsTransmitted ?: "-"}, Received = ${summary.packetsReceived ?: "-"}, Loss = ${summary.packetLossRate?.toInt() ?: "-"}%",
            style = MaterialTheme.typography.bodyMedium
        )
        if (summary.minRtt != null && summary.avgRtt != null && summary.maxRtt != null) {
            Text(
                String.format(Locale.US,"RTT: Min = %.2fms, Avg = %.2fms, Max = %.2fms", summary.minRtt, summary.avgRtt, summary.maxRtt),
                style = MaterialTheme.typography.bodyMedium
            )
            summary.stdDevRtt?.let { Text(String.format(Locale.US, "StdDev = %.2fms", it), style = MaterialTheme.typography.bodyMedium) }
        }
        if (summary.error != null && !summary.isSuccess) {
             Text("Error: ${summary.error}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else if (summary.rawOutput.isNotBlank() && summary.packetsTransmitted == null) { // If raw output contains summary not parsed fully
             Text(summary.rawOutput.lines().lastOrNull{it.isNotBlank()} ?: summary.rawOutput, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

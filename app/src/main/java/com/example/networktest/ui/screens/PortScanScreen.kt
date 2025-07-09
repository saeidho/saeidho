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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.networktest.data.model.PortScanResult
import com.example.networktest.ui.viewmodel.PortScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScanScreen(viewModel: PortScanViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
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
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.errorMessage?.contains("Host", ignoreCase = true) == true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.portsInput,
            onValueChange = { viewModel.onPortsInputChange(it) },
            label = { Text("Ports (e.g., 22,80,443 or 1-1024)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Numbers,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (uiState.canStartScan) viewModel.startPortScan(uiState.hostInput, uiState.portsInput)
            }),
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.errorMessage?.contains("Port", ignoreCase = true) == true
        )
        TextButton(onClick = { viewModel.useCommonPorts() }, modifier = Modifier.align(Alignment.End)) {
            Text("Use Common Ports")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.startPortScan(uiState.hostInput, uiState.portsInput)
                },
                enabled = uiState.canStartScan && uiState.hostInput.isNotBlank() && uiState.portsInput.isNotBlank()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Scan")
                Spacer(Modifier.width(4.dp))
                Text("Start Scan")
            }
            Button(
                onClick = { viewModel.cancelPortScan() },
                enabled = uiState.isLoading
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Scan")
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = uiState.progressPercentage / 100f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${uiState.progressPercentage}% Complete. Open: ${uiState.openPortCount}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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
        Text("Results (Open: ${uiState.openPortCount}):", style = MaterialTheme.typography.titleSmall)

        if (uiState.scanResults.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.scanResults, key = { it.port }) { result ->
                    PortScanResultItem(result)
                    Divider()
                }
            }
        } else if (!uiState.isLoading && uiState.hostInput.isNotBlank() && uiState.portsInput.isNotBlank() && uiState.errorMessage == null) {
            Text(
                "Enter host and ports, then press Start.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun PortScanResultItem(result: PortScanResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Port ${result.port}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (result.isOpen) "Open" else "Closed/Filtered",
            style = MaterialTheme.typography.bodyMedium,
            color = if (result.isOpen) Color(0xFF008000) else MaterialTheme.colorScheme.onSurfaceVariant // Dark Green for open
        )
    }
    result.error?.let {
        if (!result.isOpen) { // Show error only if port is not open to avoid redundancy with "Connection refused"
             Text(
                text = "  Error: $it",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

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
import com.example.networktest.data.model.TracerouteHop
import com.example.networktest.ui.viewmodel.TracerouteViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(viewModel: TracerouteViewModel = hiltViewModel()) {
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
                if (uiState.canStartTraceroute) viewModel.startTraceroute(uiState.hostInput)
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
                    viewModel.startTraceroute(uiState.hostInput)
                },
                enabled = uiState.canStartTraceroute && uiState.hostInput.isNotBlank()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Traceroute")
                Spacer(Modifier.width(4.dp))
                Text("Start")
            }
            Button(
                onClick = { viewModel.cancelTraceroute() },
                enabled = uiState.isLoading
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Traceroute")
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }

        if (uiState.isLoading && uiState.hops.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Traceroute in progress...", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp))
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

        if (uiState.hops.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.hops, key = { it.hop }) { hop ->
                    TracerouteHopItem(hop)
                    Divider()
                }
            }
        } else if (!uiState.isLoading && uiState.hostInput.isNotBlank() && uiState.errorMessage == null) {
            Text(
                "Enter a host and press Start to begin traceroute.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun TracerouteHopItem(hop: TracerouteHop) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%2d.".format(hop.hop),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(30.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hop.displayAddress, // hostName or IP or *
                style = MaterialTheme.typography.bodyMedium
            )
            if (hop.ipAddress != null && hop.hostName != null && hop.ipAddress != hop.hostName) {
                Text(
                    text = "(${hop.ipAddress})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        // Display RTTs or '*' for timeouts
        val rtt1Text = hop.rtt1?.let { "%.3f ms".format(Locale.US, it) } ?: "*"
        val rtt2Text = hop.rtt2?.let { "%.3f ms".format(Locale.US, it) } ?: "*"
        val rtt3Text = hop.rtt3?.let { "%.3f ms".format(Locale.US, it) } ?: "*"

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rtt1Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
            Text(rtt2Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
            Text(rtt3Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
        }
    }
    if (hop.error != null && hop.displayAddress == "* * *") { // Show specific error if all are timeouts for this hop
        Text(
            text = "    ${hop.error}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 30.dp)
        )
    } else if (hop.rawOutput.isNotBlank() && hop.rtt1 == null && hop.rtt2 == null && hop.rtt3 == null && hop.ipAddress == null) {
        // Fallback for unparsed lines that might indicate an issue
        Text(
            text = "    ${hop.rawOutput.trim()}",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp)
        )
    }
}

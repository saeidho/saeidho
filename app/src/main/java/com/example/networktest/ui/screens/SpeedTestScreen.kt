package com.example.networktest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.networktest.ui.viewmodel.SpeedTestViewModel
import java.util.Locale

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(top = 20.dp)) {
            Text("Network Speed Test", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isTesting) {
                SpeedTestProgressIndicator(progress = uiState.progressPercent / 100f)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${uiState.progressPercent}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                uiState.pingTimeMs?.let {
                     Text("Ping: $it ms", style = MaterialTheme.typography.bodyMedium)
                }

            } else {
                // Display results or initial message
                if (uiState.downloadSpeedMbps != null || uiState.uploadSpeedMbps != null || uiState.pingTimeMs != null) {
                    SpeedTestResults(uiState)
                } else {
                     Text(
                        "Press Start to begin the speed test.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            uiState.error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (!uiState.isTesting) {
                Button(
                    onClick = { viewModel.startSpeedTest() },
                    modifier = Modifier.sizeIn(minWidth = 150.dp, minHeight = 50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Test")
                    Spacer(Modifier.width(8.dp))
                    Text("Start Test", fontSize = 18.sp)
                }
            } else {
                Button(
                    onClick = { viewModel.cancelSpeedTest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.sizeIn(minWidth = 150.dp, minHeight = 50.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop Test")
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Test", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun SpeedTestProgressIndicator(progress: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 12.dp,
            strokeCap = StrokeCap.Round
        )
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            color = MaterialTheme.colorScheme.primary,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun SpeedTestResults(result: com.example.networktest.data.model.SpeedTestResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result.pingTimeMs?.let {
            ResultCard("Ping", "${it} ms")
        }
        result.downloadSpeedMbps?.let {
            ResultCard("Download", String.format(Locale.US, "%.2f Mbps", it))
        }
        result.uploadSpeedMbps?.let {
            ResultCard("Upload", String.format(Locale.US, "%.2f Mbps", it))
        }
        result.serverLocation?.let {
            Text("Server: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ResultCard(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
         Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

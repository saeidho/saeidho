package com.example.networktest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.networktest.data.model.DnsRecord
import com.example.networktest.ui.viewmodel.DnsLookupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLookupScreen(viewModel: DnsLookupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.hostnameInput,
            onValueChange = { viewModel.onHostnameInputChange(it) },
            label = { Text("Hostname (e.g., google.com)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                viewModel.performLookup(uiState.hostnameInput)
            }),
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.errorMessage?.contains("Hostname", ignoreCase = true) == true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.performLookup(uiState.hostnameInput)
            },
            enabled = !uiState.isLoading && uiState.hostnameInput.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Search, contentDescription = "Lookup DNS")
            Spacer(Modifier.width(4.dp))
            Text("Lookup")
        }

        if (uiState.isLoading) {
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

        if (uiState.dnsRecords.isNotEmpty()) {
            Text("DNS Records:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.dnsRecords) { record ->
                    DnsRecordItem(record)
                    Divider()
                }
            }
        } else if (!uiState.isLoading && uiState.hostnameInput.isNotBlank() && uiState.errorMessage == null) {
            Text(
                "Enter a hostname and press Lookup.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun DnsRecordItem(record: DnsRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = record.type,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp) // Fixed width for type
        )
        Column {
            Text(
                text = record.value,
                style = MaterialTheme.typography.bodyMedium
            )
            if (record.name != record.value && record.name != uiState.value.hostnameInput) { // Show original name if different (e.g. for CNAMEs)
                 Text(
                    text = "for ${record.name}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.ttl?.let {
                Text(
                    text = "TTL: $it",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.error?.let {
                Text(
                    text = "Error: $it",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Accessing uiState directly here is not ideal, but for simplicity in this item composable.
// Better to pass hostnameInput to DnsRecordItem if needed.
@Composable
private fun DnsRecordItemPreviewState(): DnsLookupViewModel {
    // This is a bit of a hack to get a ViewModel instance for preview.
    // In a real app, you might use a fake ViewModel or pass data directly.
    val viewModel: DnsLookupViewModel = hiltViewModel()
    return viewModel
}

// Example of how uiState might be accessed if needed in item, but it's better to pass parameters.
private val uiState @Composable get() = DnsRecordItemPreviewState().uiState.collectAsState().value

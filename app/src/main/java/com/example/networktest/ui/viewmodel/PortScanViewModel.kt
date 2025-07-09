package com.example.networktest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.PortScanResult
import com.example.networktest.data.repository.PortScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PortScanUiState(
    val hostInput: String = "",
    val portsInput: String = "", // e.g., "22,80,443" or "1-1024"
    val scanResults: List<PortScanResult> = emptyList(),
    val openPortCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canStartScan: Boolean = true,
    val progressPercentage: Int = 0 // 0-100
)

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanRepository: PortScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortScanUiState())
    val uiState: StateFlow<PortScanUiState> = _uiState.asStateFlow()

    private var portScanJob: Job? = null

    fun onHostInputChange(host: String) {
        _uiState.update { it.copy(hostInput = host, errorMessage = null) }
    }

    fun onPortsInputChange(ports: String) {
        _uiState.update { it.copy(portsInput = ports, errorMessage = null) }
    }

    fun useCommonPorts() {
        val commonPortsString = portScanRepository.getCommonPorts().joinToString(",")
        _uiState.update { it.copy(portsInput = commonPortsString, errorMessage = null) }
    }

    fun startPortScan(host: String, portsString: String, connectTimeoutMs: Int = 1000, concurrency: Int = 20) {
        if (host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Host cannot be empty.") }
            return
        }
        if (portsString.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ports cannot be empty. Try 'Common Ports'.") }
            return
        }

        val portsToScan = portScanRepository.parsePortRange(portsString)
        if (portsToScan == null || portsToScan.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Invalid port format. Use numbers, commas, and hyphens (e.g., 22,80,100-200).") }
            return
        }

        portScanJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                scanResults = emptyList(),
                openPortCount = 0,
                errorMessage = null,
                canStartScan = false,
                progressPercentage = 0
            )
        }

        var scannedCount = 0

        portScanJob = portScanRepository.scanPorts(host, portsToScan, connectTimeoutMs, concurrency)
            .onEach { result ->
                scannedCount++
                val progress = ((scannedCount.toDouble() / portsToScan.size.toDouble()) * 100).toInt()
                if (result.port == 0 && result.error != null) { // Global error from repository (e.g. host resolve failed)
                     _uiState.update {
                        it.copy(
                            errorMessage = result.error,
                            isLoading = false, // Stop loading on global error
                            canStartScan = true,
                            progressPercentage = 100 // Mark as complete due to error
                        )
                    }
                    portScanJob?.cancel() // Stop further processing
                    return@onEach
                }

                _uiState.update { currentState ->
                    val newResults = currentState.scanResults + result
                    currentState.copy(
                        scanResults = newResults.sortedBy { it.port },
                        openPortCount = newResults.count { it.isOpen },
                        progressPercentage = progress
                    )
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "An unexpected error occurred.",
                        canStartScan = true,
                        progressPercentage = 100 // Mark as complete due to error
                    )
                }
            }
            .onCompletion { cause -> // This is called on normal completion or cancellation/error
                val currentProgress = _uiState.value.progressPercentage
                _uiState.update {
                    val finalErrorMessage = if (cause != null && it.errorMessage == null) {
                        // If flow completed with an error (catch block might not have updated if it was cancellation)
                        cause.message ?: "Scan stopped unexpectedly."
                    } else {
                        it.errorMessage // Keep existing error message if any
                    }

                    it.copy(
                        isLoading = false,
                        canStartScan = true,
                        // Ensure progress is 100 if no error or if it was already set by an error condition
                        progressPercentage = if (finalErrorMessage != null && currentProgress < 100) currentProgress else 100,
                        errorMessage = finalErrorMessage
                        )
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelPortScan() {
        portScanJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (it.scanResults.isNotEmpty()) "Port scan cancelled by user." else null,
                canStartScan = true,
                progressPercentage = 100 // Mark as complete
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        portScanJob?.cancel()
    }
}

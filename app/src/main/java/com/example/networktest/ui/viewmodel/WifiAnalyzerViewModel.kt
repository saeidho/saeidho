package com.example.networktest.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.WifiNetwork
import com.example.networktest.data.repository.WifiAnalyzerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

data class WifiAnalyzerUiState(
    val wifiNetworks: List<WifiNetwork> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isScanning: Boolean = false, // To indicate an active scan request
    val missingPermissions: Boolean = false
)

@HiltViewModel
class WifiAnalyzerViewModel @Inject constructor(
    private val wifiAnalyzerRepository: WifiAnalyzerRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiAnalyzerUiState())
    val uiState: StateFlow<WifiAnalyzerUiState> = _uiState.asStateFlow()

    private var wifiAnalysisJob: Job? = null

    init {
        checkPermissionsAndLoadNetworks()
    }

    private fun checkPermissionsAndLoadNetworks() {
        if (hasRequiredPermissions()) {
            _uiState.update { it.copy(missingPermissions = false) }
            loadWifiNetworks()
        } else {
            _uiState.update { it.copy(missingPermissions = true, errorMessage = "Wi-Fi scanning requires Location and Wi-Fi permissions.") }
        }
    }

    fun refreshWifiNetworks() {
        if (hasRequiredPermissions()) {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
            val scanStarted = wifiAnalyzerRepository.startScan()
            if (!scanStarted) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Could not start Wi-Fi scan. System might be throttling requests or Wi-Fi is off.") }
            }
            // The BroadcastReceiver in the repository will trigger the flow update when results are available.
            // We can set isScanning to false once the flow emits new data or after a timeout.
            // For simplicity, we'll rely on the flow emission to eventually clear a general loading state.
        } else {
             _uiState.update { it.copy(missingPermissions = true, errorMessage = "Cannot refresh. Missing permissions.") }
        }
    }


    private fun loadWifiNetworks() {
        wifiAnalysisJob?.cancel() // Cancel previous job

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        wifiAnalysisJob = wifiAnalyzerRepository.getWifiNetworks()
            .onEach { networks ->
                _uiState.update {
                    it.copy(
                        wifiNetworks = networks,
                        isLoading = false, // Data received, stop initial loading
                        isScanning = false // Scan results are in
                    )
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isScanning = false,
                        errorMessage = e.message ?: "Error loading Wi-Fi networks.",
                        missingPermissions = e is IllegalStateException && e.message?.contains("permission") == true
                    )
                }
            }
            .onCompletion {
                 // Ensure loading/scanning flags are reset if the flow completes for any reason
                _uiState.update {
                    if (it.isLoading || it.isScanning) { // Only update if still loading/scanning
                        it.copy(isLoading = false, isScanning = false)
                    } else {
                        it
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionsGranted() {
        _uiState.update { it.copy(missingPermissions = false, errorMessage = null) }
        loadWifiNetworks()
    }

    fun onPermissionsDenied() {
        _uiState.update { it.copy(missingPermissions = true, errorMessage = "Permissions denied. Wi-Fi scanning features will not be available.") }
    }


    override fun onCleared() {
        super.onCleared()
        wifiAnalysisJob?.cancel()
    }
}

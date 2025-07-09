package com.example.networktest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.SpeedTestResult
import com.example.networktest.data.repository.SpeedTestRepository
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

// UiState will be the same as SpeedTestResult for simplicity,
// but can be expanded if more UI-specific state is needed.
typealias SpeedTestUiState = SpeedTestResult

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val speedTestRepository: SpeedTestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedTestUiState(isTesting = false)) // Initial state not testing
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var speedTestJob: Job? = null

    fun startSpeedTest() {
        if (_uiState.value.isTesting) {
            // Optionally, allow restart or show message "Test already in progress"
            return
        }

        speedTestJob?.cancel() // Cancel any previous job just in case

        // Reset state before starting a new test
        _uiState.value = SpeedTestUiState(isTesting = true, progressPercent = 0)


        speedTestJob = speedTestRepository.performSpeedTest()
            .onEach { result ->
                _uiState.value = result // Update the entire state with the latest result from the flow
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        error = e.message ?: "An unexpected error occurred.",
                        progressPercent = it.progressPercent // Keep last known progress on error
                    )
                }
            }
            .onCompletion {
                // This is called on normal completion, cancellation, or error (after catch).
                // Ensure isTesting is false. If there was an error, it should be in uiState already.
                // If it completed without error but also without setting isTesting = false (e.g. flow ended prematurely),
                // set it here.
                if (_uiState.value.isTesting) { // If still marked as testing
                    _uiState.update {
                        val finalErrorMessage = if (it.downloadSpeedMbps == null && it.error == null) {
                            "Speed test did not complete."
                        } else {
                            it.error
                        }
                        it.copy(isTesting = false, error = finalErrorMessage, progressPercent = 100)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelSpeedTest() {
        speedTestJob?.cancel()
        // Update UI state to reflect cancellation
        // The onCompletion block of the flow should handle setting isTesting to false.
        // We can add a specific cancellation message if desired.
        _uiState.update {
            if (it.isTesting) { // Only update if a test was actually running
                it.copy(
                    isTesting = false,
                    error = "Speed test cancelled by user.",
                    progressPercent = 100 // Or keep last progress
                )
            } else {
                it // No change if not testing
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speedTestJob?.cancel()
    }
}

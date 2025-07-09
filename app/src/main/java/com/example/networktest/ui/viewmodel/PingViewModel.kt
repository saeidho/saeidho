package com.example.networktest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.PingResult
import com.example.networktest.data.repository.PingRepository
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

/**
 * UI state for the Ping screen.
 * @property hostInput The current host input by the user.
 * @property pingResults List of individual ping results received.
 * @property summaryResult The final summary ping result.
 * @property isLoading True if a ping operation is currently in progress.
 * @property errorMessage An error message to display, if any.
 * @property canStartPing True if a new ping operation can be started.
 */
data class PingUiState(
    val hostInput: String = "",
    val pingResults: List<PingResult> = emptyList(),
    val summaryResult: PingResult? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canStartPing: Boolean = true
)

/**
 * ViewModel for the Ping screen.
 * Manages the UI state and interactions for ping operations.
 */
@HiltViewModel
class PingViewModel @Inject constructor(
    private val pingRepository: PingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PingUiState())
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    private var pingJob: Job? = null

    fun onHostInputChange(host: String) {
        _uiState.update { it.copy(hostInput = host, errorMessage = null) }
    }

    fun startPing(host: String, count: Int = 4, timeoutSeconds: Int = 1) {
        if (host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Host cannot be empty.") }
            return
        }

        pingJob?.cancel() // Cancel any ongoing ping

        _uiState.update {
            it.copy(
                isLoading = true,
                pingResults = emptyList(),
                summaryResult = null,
                errorMessage = null,
                canStartPing = false // Disable start button during ping
            )
        }

        pingJob = pingRepository.ping(host, count, timeoutSeconds)
            .onEach { pingResult ->
                if (pingResult.packetsTransmitted != null || pingResult.error?.startsWith("Ping command failed") == true) { // This looks like a summary line
                    _uiState.update {
                        it.copy(
                            summaryResult = pingResult,
                            // If it's an error summary, keep it as an error message too
                            errorMessage = if (pingResult.isSuccess) it.errorMessage else pingResult.error ?: it.errorMessage
                        )
                    }
                } else if (pingResult.error != null && pingResult.rawOutput.startsWith("Pinging")) { // Initial informational message
                     _uiState.update { currentState ->
                        currentState.copy(
                            pingResults = currentState.pingResults + pingResult.copy(error = null) // Clear error for initial message
                        )
                    }
                }
                else { // Individual ping reply or intermediate error
                    _uiState.update { currentState ->
                        currentState.copy(
                            pingResults = currentState.pingResults + pingResult,
                            errorMessage = if (pingResult.error != null && pingResult.timeTakenMs == null) pingResult.error else currentState.errorMessage // Show line error if no time
                        )
                    }
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "An unexpected error occurred.",
                        canStartPing = true // Re-enable start button
                    )
                }
            }
            .onCompletion {
                // This block is called when the flow completes normally or is cancelled.
                // If cancelled, isLoading might still be true from the catch block if an error occurred during cancellation.
                // If completed normally, isLoading should be false.
                _uiState.update {
                    val finalErrorMessage = if (it.summaryResult?.isSuccess == false) {
                        it.summaryResult.error
                    } else if (it.pingResults.isEmpty() && it.summaryResult == null) {
                        "No results received."
                    } else {
                        it.errorMessage // Keep existing error if any, or null
                    }
                    it.copy(isLoading = false, canStartPing = true, errorMessage = finalErrorMessage)
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelPing() {
        pingJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (it.pingResults.isNotEmpty()) "Ping cancelled by user." else null,
                canStartPing = true
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
    }
}

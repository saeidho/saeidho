package com.example.networktest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.TracerouteHop
import com.example.networktest.data.repository.TracerouteRepository
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

data class TracerouteUiState(
    val hostInput: String = "",
    val hops: List<TracerouteHop> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canStartTraceroute: Boolean = true
)

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracerouteRepository: TracerouteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TracerouteUiState())
    val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()

    private var tracerouteJob: Job? = null

    fun onHostInputChange(host: String) {
        _uiState.update { it.copy(hostInput = host, errorMessage = null) }
    }

    fun startTraceroute(host: String, maxHops: Int = 30, timeoutSeconds: Int = 3) {
        if (host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Host cannot be empty.") }
            return
        }

        tracerouteJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                hops = emptyList(),
                errorMessage = null,
                canStartTraceroute = false
            )
        }

        tracerouteJob = tracerouteRepository.traceroute(host, maxHops, timeoutSeconds)
            .onEach { hop ->
                // If it's an error-only hop (hop=0 from repo), show as general error
                if (hop.hop == 0 && hop.error != null) {
                     _uiState.update { it.copy(errorMessage = hop.error) }
                } else {
                    _uiState.update { currentState ->
                        // Check if this hop number already exists, update it, otherwise add new
                        val existingHopIndex = currentState.hops.indexOfFirst { h -> h.hop == hop.hop }
                        val updatedHops = if (existingHopIndex != -1) {
                            currentState.hops.toMutableList().apply { this[existingHopIndex] = hop }
                        } else {
                            currentState.hops + hop
                        }
                        currentState.copy(hops = updatedHops.sortedBy { it.hop })
                    }
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "An unexpected error occurred.",
                        canStartTraceroute = true
                    )
                }
            }
            .onCompletion {
                // This is called on normal completion or cancellation
                _uiState.update {
                    val finalErrorMessage = if (it.hops.isEmpty() && it.errorMessage == null) {
                        "No traceroute results."
                    } else {
                        it.errorMessage
                    }
                    it.copy(isLoading = false, canStartTraceroute = true, errorMessage = finalErrorMessage)
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelTraceroute() {
        tracerouteJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (it.hops.isNotEmpty()) "Traceroute cancelled by user." else null,
                canStartTraceroute = true
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tracerouteJob?.cancel()
    }
}

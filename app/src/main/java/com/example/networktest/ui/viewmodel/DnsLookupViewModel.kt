package com.example.networktest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.DnsRecord
import com.example.networktest.data.repository.DnsLookupRepository
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

data class DnsLookupUiState(
    val hostnameInput: String = "",
    val dnsRecords: List<DnsRecord> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DnsLookupViewModel @Inject constructor(
    private val dnsLookupRepository: DnsLookupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsLookupUiState())
    val uiState: StateFlow<DnsLookupUiState> = _uiState.asStateFlow()

    private var lookupJob: Job? = null

    fun onHostnameInputChange(hostname: String) {
        _uiState.update { it.copy(hostnameInput = hostname, errorMessage = null) }
    }

    fun performLookup(hostname: String) {
        if (hostname.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Hostname cannot be empty.") }
            return
        }

        lookupJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                dnsRecords = emptyList(),
                errorMessage = null
            )
        }

        lookupJob = dnsLookupRepository.lookupDns(hostname)
            .onEach { records ->
                // Check if the only record is an error record from the repository
                if (records.size == 1 && records.first().type == "ERROR" && records.first().error != null) {
                     _uiState.update {
                        it.copy(
                            dnsRecords = emptyList(), // No valid records to show
                            errorMessage = records.first().error,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            dnsRecords = records,
                            isLoading = false,
                            // Clear error message if we got some valid records,
                            // even if some individual ones might have errors (not handled yet)
                            errorMessage = if (records.any{ rec -> rec.error == null}) null else it.errorMessage
                        )
                    }
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "An unexpected error occurred during DNS lookup."
                    )
                }
            }
            .onCompletion {
                // Ensure loading is false if not already set by onEach or catch
                 _uiState.update { currentState ->
                    if (currentState.isLoading) { // If still loading (e.g. flow completed empty without error)
                        currentState.copy(
                            isLoading = false,
                            errorMessage = if(currentState.dnsRecords.isEmpty() && currentState.errorMessage == null) "No DNS records found." else currentState.errorMessage
                        )
                    } else {
                        currentState
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        lookupJob?.cancel()
    }
}

package com.example.networktest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktest.data.model.IpInformation
import com.example.networktest.data.repository.IpInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IpInfoUiState(
    val localIpInfo: IpInformation? = null,
    val publicIpAddress: String? = null,
    val isLoadingLocalIp: Boolean = false,
    val isLoadingPublicIp: Boolean = false,
    val errorMessage: String? = null
) {
    val isLoading: Boolean get() = isLoadingLocalIp || isLoadingPublicIp
}

@HiltViewModel
class IpInfoViewModel @Inject constructor(
    private val ipInfoRepository: IpInfoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IpInfoUiState())
    val uiState: StateFlow<IpInfoUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadIpInformation()
    }

    fun loadIpInformation() {
        loadJob?.cancel() // Cancel previous job if any

        // Reset error message and set loading states
        _uiState.update { it.copy(errorMessage = null) }

        // Use a single job to manage both fetches.
        // The isLoading flags within UiState will manage individual loading states.
        loadJob = viewModelScope.launch {
            // Fetch Local IP Information
            ipInfoRepository.getIpInformation()
                .onStart { _uiState.update { it.copy(isLoadingLocalIp = true) } }
                .onEach { ipInfo ->
                    _uiState.update { it.copy(localIpInfo = ipInfo, isLoadingLocalIp = false) }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = "Local IP: ${e.message ?: "Error fetching local IP"}",
                            isLoadingLocalIp = false
                        )
                    }
                }
                .launchIn(viewModelScope) // Launch this flow independently within the job's scope

            // Fetch Public IP Address
            ipInfoRepository.fetchPublicIpAddress()
                .onStart { _uiState.update { it.copy(isLoadingPublicIp = true) } }
                .onEach { publicIp ->
                    _uiState.update { oldState ->
                        // Augment the existing localIpInfo with the public IP if available
                        val updatedLocalIpInfo = oldState.localIpInfo?.copy(publicIpAddress = publicIp)
                                                 ?: IpInformation(publicIpAddress = publicIp, localIpAddress = null, gateway = null, subnetMask = null, dnsServers = null)

                        oldState.copy(
                            publicIpAddress = publicIp,
                            localIpInfo = updatedLocalIpInfo, // Store it within localIpInfo as well
                            isLoadingPublicIp = false,
                            errorMessage = if (publicIp == null && oldState.errorMessage == null) "Could not fetch public IP." else oldState.errorMessage
                        )
                    }
                }
                .catch { e ->
                    _uiState.update {
                        val existingError = it.errorMessage
                        val publicIpError = "Public IP: ${e.message ?: "Error fetching public IP"}"
                        it.copy(
                            errorMessage = if (existingError != null) "$existingError\n$publicIpError" else publicIpError,
                            isLoadingPublicIp = false
                        )
                    }
                }
                .launchIn(viewModelScope) // Launch this flow independently
        }
    }

    // Alternative combined approach, but might be less clear for individual loading states
    // if you want fine-grained control over "local loading" vs "public loading" in UI.
    fun loadIpInformationCombined() {
        loadJob?.cancel()
        _uiState.update { it.copy(errorMessage = null, isLoadingLocalIp = true, isLoadingPublicIp = true) }

        loadJob = combine(
            ipInfoRepository.getIpInformation(),
            ipInfoRepository.fetchPublicIpAddress()
        ) { localInfo, publicIp ->
            Pair(localInfo, publicIp)
        }
        .onEach { (localInfo, publicIp) ->
            _uiState.update {
                it.copy(
                    localIpInfo = localInfo.copy(publicIpAddress = publicIp),
                    publicIpAddress = publicIp, // Keep separate for direct access if needed
                    isLoadingLocalIp = false,
                    isLoadingPublicIp = false,
                    errorMessage = if (publicIp == null && localInfo.localIpAddress == null) "Failed to fetch any IP information." else it.errorMessage
                )
            }
        }
        .catch { e ->
            _uiState.update {
                it.copy(
                    isLoadingLocalIp = false,
                    isLoadingPublicIp = false,
                    errorMessage = e.message ?: "An error occurred."
                )
            }
        }
        .launchIn(viewModelScope)
    }


    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}

package com.example.networktest.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.networktest.data.model.IpInformation
import com.example.networktest.data.repository.IpInfoRepository
import com.example.networktest.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class IpInfoViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: IpInfoViewModel
    private lateinit var mockIpInfoRepository: IpInfoRepository

    @Before
    fun setUp() {
        mockIpInfoRepository = mock()
    }

    @Test
    fun `init loads IP information successfully`() = runTest {
        val localIp = "192.168.1.100"
        val publicIp = "8.8.8.8"
        val mockLocalInfo = IpInformation(localIpAddress = localIp, gateway = "192.168.1.1", subnetMask = "255.255.255.0", dnsServers = listOf("1.1.1.1"), connectionType = "Wi-Fi")

        whenever(mockIpInfoRepository.getIpInformation()).thenReturn(flowOf(mockLocalInfo))
        whenever(mockIpInfoRepository.fetchPublicIpAddress()).thenReturn(flowOf(publicIp))

        // ViewModel is initialized here, which triggers loadIpInformation
        viewModel = IpInfoViewModel(mockIpInfoRepository)

        // Allow flows to emit and be collected
        advanceUntilIdle()


        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.localIpInfo)
        assertEquals(localIp, state.localIpInfo?.localIpAddress)
        assertEquals(publicIp, state.publicIpAddress)
        assertEquals(publicIp, state.localIpInfo?.publicIpAddress) // Check if public IP is added to localInfo
        assertEquals("Wi-Fi", state.localIpInfo?.connectionType)
    }

    @Test
    fun `init loads local IP fails, public IP succeeds`() = runTest {
        val publicIp = "8.8.4.4"
        val localErrorMessage = "Failed to get local IP"

        whenever(mockIpInfoRepository.getIpInformation()).thenReturn(flow { throw RuntimeException(localErrorMessage) })
        whenever(mockIpInfoRepository.fetchPublicIpAddress()).thenReturn(flowOf(publicIp))

        viewModel = IpInfoViewModel(mockIpInfoRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains(localErrorMessage))
        // Even if local fails, public IP info should be there
        assertNotNull(state.publicIpAddress)
        assertEquals(publicIp, state.publicIpAddress)
        // localIpInfo might be partially populated with public IP or be null depending on how error is handled for combined state
        // In current implementation, localIpInfo will still hold publicIpAddress if it's successfully fetched.
        assertEquals(publicIp, state.localIpInfo?.publicIpAddress)
        assertNull(state.localIpInfo?.localIpAddress)
    }

    @Test
    fun `init loads local IP succeeds, public IP fails`() = runTest {
        val localIp = "10.0.0.5"
        val mockLocalInfo = IpInformation(localIpAddress = localIp, gateway = "10.0.0.1", connectionType = "Ethernet", subnetMask = null, dnsServers = null)
        val publicErrorMessage = "Failed to fetch public IP"

        whenever(mockIpInfoRepository.getIpInformation()).thenReturn(flowOf(mockLocalInfo))
        whenever(mockIpInfoRepository.fetchPublicIpAddress()).thenReturn(flow { throw RuntimeException(publicErrorMessage) })

        viewModel = IpInfoViewModel(mockIpInfoRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains(publicErrorMessage))
        assertNotNull(state.localIpInfo)
        assertEquals(localIp, state.localIpInfo?.localIpAddress)
        assertNull(state.publicIpAddress) // Public IP fetch failed
        assertNull(state.localIpInfo?.publicIpAddress) // Should also be null in the combined object
    }

    @Test
    fun `refreshIpInformation re-fetches data`() = runTest {
        // Initial setup: Both fail first
        whenever(mockIpInfoRepository.getIpInformation()).thenReturn(flow { throw RuntimeException("Initial local error") })
        whenever(mockIpInfoRepository.fetchPublicIpAddress()).thenReturn(flow { throw RuntimeException("Initial public error") })

        viewModel = IpInfoViewModel(mockIpInfoRepository)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Initial local error"))
        assertTrue(state.errorMessage!!.contains("Initial public error"))


        // Setup for successful refresh
        val newLocalIp = "192.168.2.2"
        val newPublicIp = "1.2.3.4"
        val newMockLocalInfo = IpInformation(localIpAddress = newLocalIp, gateway = "192.168.2.1", connectionType = "Wi-Fi", subnetMask = null, dnsServers = null)
        whenever(mockIpInfoRepository.getIpInformation()).thenReturn(flowOf(newMockLocalInfo))
        whenever(mockIpInfoRepository.fetchPublicIpAddress()).thenReturn(flowOf(newPublicIp))

        viewModel.loadIpInformation() // Trigger refresh
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage) // Errors should be cleared on successful refresh
        assertNotNull(state.localIpInfo)
        assertEquals(newLocalIp, state.localIpInfo?.localIpAddress)
        assertEquals(newPublicIp, state.publicIpAddress)
    }
}

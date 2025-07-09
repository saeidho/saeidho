package com.example.networktest.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.networktest.data.model.PingResult
import com.example.networktest.data.repository.PingRepository
import com.example.networktest.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class PingViewModelTest {

    // This rule swaps the main dispatcher for a test dispatcher
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    // This rule is for LiveData, but good to have if ViewModels ever use it.
    // For StateFlow, it's not strictly necessary but doesn't hurt.
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: PingViewModel
    private lateinit var mockPingRepository: PingRepository

    @Before
    fun setUp() {
        mockPingRepository = mock()
        viewModel = PingViewModel(mockPingRepository)
    }

    @Test
    fun `onHostInputChange updates hostInput in uiState`() {
        val testHost = "google.com"
        viewModel.onHostInputChange(testHost)
        assertEquals(testHost, viewModel.uiState.value.hostInput)
        assertNull(viewModel.uiState.value.errorMessage) // Should clear error on input change
    }

    @Test
    fun `startPing with blank host sets error message`() = runTest {
        viewModel.startPing("")
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Host cannot be empty.", viewModel.uiState.value.errorMessage)
        verify(mockPingRepository, never()).ping(any(), any(), any())
    }

    @Test
    fun `startPing success updates uiState correctly`() = runTest {
        val host = "example.com"
        val mockPingResult1 = PingResult(rawOutput = "Pinging example.com [1.2.3.4]...", ipAddress = "1.2.3.4")
        val mockPingResult2 = PingResult(ipAddress = "1.2.3.4", timeTakenMs = 10f, ttl = 50, rawOutput = "Reply from 1.2.3.4: ... time=10ms TTL=50")
        val mockSummary = PingResult(packetsTransmitted = 1, packetsReceived = 1, avgRtt = 10f, rawOutput = "--- example.com ping statistics ---")

        whenever(mockPingRepository.ping(host, 4, 1)).thenReturn(flowOf(mockPingResult1, mockPingResult2, mockSummary))

        viewModel.startPing(host)

        // Initial state after starting
        assertTrue(viewModel.uiState.value.isLoading || !viewModel.uiState.value.canStartPing) // Should be loading or ping started

        // Advance past the flow collection
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()


        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertTrue(finalState.canStartPing)
        assertNull(finalState.errorMessage)
        assertEquals(2, finalState.pingResults.size) // mockPingResult1 and mockPingResult2
        assertEquals(mockPingResult1.copy(error = null), finalState.pingResults[0]) // Initial messages' error is cleared
        assertEquals(mockPingResult2, finalState.pingResults[1])
        assertEquals(mockSummary, finalState.summaryResult)
    }

    @Test
    fun `startPing failure updates uiState with error`() = runTest {
        val host = "unknown.host"
        val errorMessage = "Unknown host"
        // Simulate an error from the repository (e.g., host not found, then a summary-like error)
        val errorResult = PingResult(rawOutput = "ping: unknown host unknown.host", error = errorMessage)
        val summaryError = PingResult(rawOutput = "--- unknown.host ping statistics ---", packetsTransmitted = 1, packetsReceived = 0, error = "100% packet loss", isSuccess = false)

        whenever(mockPingRepository.ping(host, 4, 1)).thenReturn(flowOf(errorResult, summaryError))

        viewModel.startPing(host)
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertTrue(finalState.canStartPing)
        // The summary error should take precedence if it's more specific
        assertEquals(summaryError.error, finalState.errorMessage)
        assertEquals(1, finalState.pingResults.size)
        assertEquals(errorResult, finalState.pingResults[0])
        assertEquals(summaryError, finalState.summaryResult)

    }

    @Test
    fun `startPing with repository throwing exception`() = runTest {
        val host = "error.host"
        val exceptionMessage = "Network is unreachable"
        whenever(mockPingRepository.ping(host, 4, 1)).thenReturn(flow { throw RuntimeException(exceptionMessage) })

        viewModel.startPing(host)
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertTrue(finalState.canStartPing)
        assertEquals(exceptionMessage, finalState.errorMessage)
        assertTrue(finalState.pingResults.isEmpty())
        assertNull(finalState.summaryResult)
    }


    @Test
    fun `cancelPing stops loading and sets canStartPing`() = runTest {
        val host = "cancellable.com"
        // A flow that never completes unless cancelled
        val longRunningFlow = flow<PingResult> {
            emit(PingResult(rawOutput = "Pinging...", ipAddress = "1.1.1.1"))
            kotlinx.coroutines.delay(5000) // Simulate long running ping
            emit(PingResult(rawOutput = "Should not see this", timeTakenMs = 10f))
        }
        whenever(mockPingRepository.ping(host, 4, 1)).thenReturn(longRunningFlow)

        viewModel.startPing(host)
        // Let the first item emit
        mainCoroutineRule.testDispatcher.scheduler.advanceTimeBy(100)


        assertTrue(viewModel.uiState.value.isLoading || !viewModel.uiState.value.canStartPing)
        assertEquals(1, viewModel.uiState.value.pingResults.size)


        viewModel.cancelPing()
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertTrue(finalState.canStartPing)
        assertEquals("Ping cancelled by user.", finalState.errorMessage)
        assertEquals(1, finalState.pingResults.size) // Only the first result before cancellation
    }
}

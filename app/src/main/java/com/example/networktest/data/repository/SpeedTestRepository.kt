package com.example.networktest.data.repository

import com.example.networktest.data.model.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlin.math.roundToLong

class SpeedTestRepository @Inject constructor() {

    // Using a publicly available test file.
    // Consider hosting your own or using files from CDNs that allow this.
    // File sizes:
    // 10MB: http://ipv4.download.thinkbroadband.com/10MB.zip
    // 5MB: http://ipv4.download.thinkbroadband.com/5MB.zip
    // Small for ping test: http://ipv4.download.thinkbroadband.com/512KB.zip
    private val defaultDownloadUrl = "http://ipv4.download.thinkbroadband.com/10MB.zip"
    // A smaller file for a quicker "ping" like test or initial connection check
    private val pingLikeDownloadUrl = "http://ipv4.download.thinkbroadband.com/512KB.zip"


    fun performSpeedTest(
        downloadUrl: String = defaultDownloadUrl
    ): Flow<SpeedTestResult> = channelFlow {
        send(SpeedTestResult(isTesting = true, progressPercent = 0))

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var downloadSpeedMbps: Double? = null
        var finalError: String? = null
        var initialPingMs: Long? = null

        try {
            // 1. Initial "Ping" test (Optional, but good for responsiveness)
            // We can measure time to connect and get headers for a small file.
            try {
                val pingUrl = URL(pingLikeDownloadUrl)
                val pingStartTime = System.currentTimeMillis()
                val pingConnection = pingUrl.openConnection() as HttpURLConnection
                pingConnection.requestMethod = "GET"
                pingConnection.connectTimeout = 5000 // 5 seconds
                pingConnection.readTimeout = 5000
                pingConnection.connect() // Establish connection

                if (pingConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    initialPingMs = System.currentTimeMillis() - pingStartTime
                    send(SpeedTestResult(isTesting = true, progressPercent = 5, pingTimeMs = initialPingMs))
                } else {
                     send(SpeedTestResult(isTesting = true, progressPercent = 5, error = "Ping-like test failed: ${pingConnection.responseMessage}"))
                }
                pingConnection.disconnect()
            } catch (e: Exception) {
                initialPingMs = null // Ping failed
                send(SpeedTestResult(isTesting = true, progressPercent = 5, error = "Ping-like test error: ${e.message}"))
            }


            // 2. Main Download Test
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Set reasonable timeouts
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 30000    // 30 seconds (for the whole download of this file)

            val startTime = System.currentTimeMillis()
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val fileSize = connection.contentLengthLong
                if (fileSize <= 0) {
                    finalError = "Could not determine file size or file is empty."
                    send(SpeedTestResult(isTesting = false, error = finalError, pingTimeMs = initialPingMs))
                    return@channelFlow
                }

                inputStream = connection.inputStream
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                var totalBytesRead: Long = 0
                var lastProgressEmitTime = System.currentTimeMillis()


                while (inputStream.read(buffer).also { bytesRead = it } != -1 && isActive) {
                    totalBytesRead += bytesRead
                    val progress = ((totalBytesRead.toDouble() / fileSize.toDouble()) * 100).toInt()

                    // Emit progress updates not too frequently to avoid overwhelming the UI
                    // We scale progress from 5% (after ping) to 95% for download part
                    val displayProgress = 5 + (progress * 0.90).toInt()

                    if (System.currentTimeMillis() - lastProgressEmitTime > 200 || displayProgress == 95) {
                         send(SpeedTestResult(isTesting = true, progressPercent = displayProgress.coerceAtMost(95), pingTimeMs = initialPingMs))
                         lastProgressEmitTime = System.currentTimeMillis()
                    }

                    // Artificial delay to simulate slower networks for testing UI, REMOVE FOR PRODUCTION
                    // if (totalBytesRead < fileSize / 2) delay(5) // Slight delay for first half
                }

                if (!isActive) { // Coroutine was cancelled
                    finalError = "Test cancelled."
                    send(SpeedTestResult(isTesting = false, error = finalError, pingTimeMs = initialPingMs))
                    return@channelFlow
                }

                val endTime = System.currentTimeMillis()
                val durationSeconds = (endTime - startTime) / 1000.0

                if (durationSeconds > 0 && totalBytesRead == fileSize) {
                    // Speed in Bytes per second
                    val speedBps = totalBytesRead / durationSeconds
                    // Speed in Megabits per second (Mbps)
                    downloadSpeedMbps = (speedBps * 8) / (1024 * 1024)
                    send(SpeedTestResult(
                        downloadSpeedMbps = downloadSpeedMbps,
                        pingTimeMs = initialPingMs,
                        progressPercent = 100,
                        isTesting = false
                    ))
                } else if (totalBytesRead != fileSize) {
                    finalError = "File download incomplete. Read $totalBytesRead of $fileSize bytes."
                } else {
                    finalError = "Download duration was too short to measure accurately."
                }

            } else {
                finalError = "Server error: ${connection.responseCode} ${connection.responseMessage}"
            }

        } catch (e: Exception) {
            if (isActive) { // Only send error if coroutine is still active
                finalError = e.message ?: "An unknown error occurred during speed test."
            } else {
                finalError = "Test cancelled by user."
            }
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) { /* ignore */ }
            connection?.disconnect()

            if (!isActive && finalError == null) { // If cancelled but no specific error set yet
                finalError = "Test cancelled."
            }

            // Send final result, whether success or error
            if (finalError != null) {
                 send(SpeedTestResult(
                    downloadSpeedMbps = downloadSpeedMbps, // Might have partial data if error occurred mid-test
                    pingTimeMs = initialPingMs,
                    error = finalError,
                    progressPercent = if (downloadSpeedMbps != null) 100 else (channel.tryReceive().getOrNull()?.progressPercent ?: 0), // keep last progress if error
                    isTesting = false))
            } else if (downloadSpeedMbps == null && isActive) { // Should not happen if no error, but as a fallback
                 send(SpeedTestResult(error = "Speed test did not complete successfully.", isTesting = false, pingTimeMs = initialPingMs))
            }
            close() // Ensure the channel is closed
        }
    }.flowOn(Dispatchers.IO)

    // Upload test would require a server endpoint.
    // fun performUploadTest(...): Flow<SpeedTestResult> = flow { ... }
}

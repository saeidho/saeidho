package com.example.networktest.data.repository

import com.example.networktest.data.model.PortScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

class PortScanRepository @Inject constructor() {

    private val defaultConnectTimeoutMs = 1000 // 1 second timeout for each port connection attempt

    fun scanPorts(
        host: String,
        ports: List<Int>,
        connectTimeoutMs: Int = defaultConnectTimeoutMs,
        concurrency: Int = 10 // Number of ports to scan concurrently
    ): Flow<PortScanResult> = channelFlow {
        if (host.isBlank()) {
            send(PortScanResult(port = 0, isOpen = false, error = "Host cannot be empty"))
            close()
            return@channelFlow
        }

        if (!host.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
            send(PortScanResult(port = 0, isOpen = false, error = "Invalid host format"))
            close()
            return@channelFlow
        }

        if (ports.isEmpty()) {
            send(PortScanResult(port = 0, isOpen = false, error = "Port list cannot be empty"))
            close()
            return@channelFlow
        }

        // Resolve hostname to IP address first.
        // Port scanning typically requires an IP address.
        val ipAddress = try {
            java.net.InetAddress.getByName(host).hostAddress
        } catch (e: Exception) {
            send(PortScanResult(port = 0, isOpen = false, error = "Could not resolve host: ${e.message}"))
            close()
            return@channelFlow
        }

        ports.chunked(concurrency).forEach { portChunk ->
            // Process each chunk of ports concurrently
            val deferredResults = portChunk.map { port ->
                // Launch a new coroutine for each port in the chunk
                async(Dispatchers.IO) { // Perform socket connection on IO dispatcher
                    try {
                        Socket().use { socket ->
                            // Set a timeout for the connection attempt
                            socket.connect(InetSocketAddress(ipAddress, port), connectTimeoutMs)
                            // If connect succeeds, the port is open
                            PortScanResult(port = port, isOpen = true)
                            // Optionally, try to grab a banner here if socket.isConnected
                            // val banner = readBanner(socket)
                            // PortScanResult(port = port, isOpen = true, banner = banner)
                        }
                    } catch (e: java.net.ConnectException) {
                        // Connection refused or timed out - port is likely closed or filtered
                        PortScanResult(port = port, isOpen = false, error = "Connection refused/timeout")
                    } catch (e: java.net.SocketTimeoutException) {
                        // Explicit timeout
                        PortScanResult(port = port, isOpen = false, error = "Connection timed out")
                    } catch (e: Exception) {
                        // Other errors
                        PortScanResult(port = port, isOpen = false, error = e.message ?: "Unknown error")
                    }
                }
            }
            // Send results as they complete
            deferredResults.forEach { deferred ->
                send(deferred.await())
            }
        }
        close() // Close the channel when all ports are scanned
    }.flowOn(Dispatchers.IO) // General dispatcher for the flow setup, actual work is on IO inside async

    // Common ports list helper
    fun getCommonPorts(): List<Int> {
        return listOf(
            20, // FTP Data
            21, // FTP Control
            22, // SSH
            23, // Telnet
            25, // SMTP
            53, // DNS
            80, // HTTP
            110, // POP3
            123, // NTP
            135, // Microsoft RPC
            137, // NetBIOS Name Service
            138, // NetBIOS Datagram Service
            139, // NetBIOS Session Service
            143, // IMAP
            443, // HTTPS (HTTP Secure)
            445, // Microsoft-DS (SMB, CIFS)
            465, // SMTPS (SMTP Secure)
            587, // SMTP (Mail Submission)
            993, // IMAPS (IMAP Secure)
            995, // POP3S (POP3 Secure)
            1080, // SOCKS Proxy
            1433, // Microsoft SQL Server
            1521, // Oracle Database
            1723, // PPTP (VPN)
            3306, // MySQL
            3389, // RDP (Remote Desktop)
            5432, // PostgreSQL
            5900, // VNC
            8000, // Common alternative HTTP/dev server
            8080, // Common alternative HTTP/proxy
            8443  // Common alternative HTTPS
        )
    }

    fun parsePortRange(rangeString: String): List<Int>? {
        if (rangeString.isBlank()) return null
        val ports = mutableListOf<Int>()
        try {
            val parts = rangeString.split(",")
            for (part in parts) {
                if (part.contains("-")) {
                    val rangeParts = part.split("-")
                    if (rangeParts.size == 2) {
                        val start = rangeParts[0].trim().toInt()
                        val end = rangeParts[1].trim().toInt()
                        if (start < 1 || end > 65535 || start > end) return null // Invalid range
                        (start..end).forEach { ports.add(it) }
                    } else {
                        return null // Invalid format
                    }
                } else {
                    val port = part.trim().toInt()
                    if (port < 1 || port > 65535) return null // Invalid port
                    ports.add(port)
                }
            }
            return ports.distinct().sorted()
        } catch (e: NumberFormatException) {
            return null // Invalid number in input
        }
    }

    // Basic banner grabbing - can be expanded
    /*
    private fun readBanner(socket: Socket, readTimeoutMs: Int = 500): String? {
        return try {
            socket.soTimeout = readTimeoutMs
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            // Read a limited amount of data to avoid blocking indefinitely on non-standard services
            val buffer = CharArray(256)
            val bytesRead = reader.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                String(buffer, 0, bytesRead).trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null // Error reading banner (e.g. timeout, connection reset)
        }
    }
    */
}

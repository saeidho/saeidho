package com.example.networktest.data.repository

import com.example.networktest.data.model.PingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Repository for performing ping operations.
 * This repository uses the system's ping command to send ICMP requests to a specified host.
 */
class PingRepository @Inject constructor() {

    /**
     * Executes a ping command to the specified host and emits [PingResult] objects.
     * The flow will emit individual ping replies as they are received, followed by a
     * summary [PingResult] at the end if the ping command provides one.
     * Errors during the ping process or parsing are also emitted as [PingResult] with the error field set.
     *
     * @param host The hostname or IP address to ping.
     * @param count The number of ping packets to send. Defaults to 4.
     * @param timeoutSeconds The timeout in seconds for each ping packet. Defaults to 1.
     * @return A [Flow] of [PingResult] objects.
     */
    fun ping(host: String, count: Int = 4, timeoutSeconds: Int = 1): Flow<PingResult> = flow {
        if (host.isBlank()) {
            emit(PingResult(rawOutput = "Host cannot be empty.", error = "Invalid host"))
            return@flow
        }

        // Basic validation for host to prevent command injection, though more robust validation might be needed.
        if (!host.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
            emit(PingResult(rawOutput = "Invalid characters in host.", error = "Invalid host format"))
            return@flow
        }


        // Attempt to get the IP address first if it's a hostname
        // This helps in displaying the IP even if ping fails later for other reasons
        var ipAddress: String? = null
        try {
            ipAddress = java.net.InetAddress.getByName(host).hostAddress
        } catch (e: Exception) {
            // Could not resolve host, but we can still try to ping it directly
            // The ping command itself might resolve it or fail.
        }

        val command = mutableListOf<String>()
        val osName = System.getProperty("os.name", "").toLowerCase()

        // Command construction varies by OS. Android uses Linux-style ping.
        command.add("ping")
        command.add("-c")
        command.add(count.toString())
        command.add("-W") // Timeout for each packet in seconds (Linux ping)
        command.add(timeoutSeconds.toString())
        // command.add("-i") // Interval in seconds (Linux ping) - consider adding if needed
        // command.add("0.2") // Example: 0.2 seconds interval
        command.add(host)

        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true) // Combine stdout and stderr
            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val fullOutput = StringBuilder()

            // Emit initial state with IP if resolved
            emit(PingResult(ipAddress = ipAddress, rawOutput = "Pinging $host (${ipAddress ?: "resolving..."})...", error = null))


            while (reader.readLine().also { line = it } != null) {
                fullOutput.append(line).append("\n")
                // Simple intermediate parsing for live updates (can be made more sophisticated)
                // This is a very basic way to show live pings.
                // A more robust solution would parse individual reply lines.
                if (line!!.contains("bytes from") && line!!.contains("time=")) {
                     try {
                        val timeMatch = Regex("time=([0-9.]+) ms").find(line!!)
                        val time = timeMatch?.groups?.get(1)?.value?.toFloatOrNull()
                        val ttlMatch = Regex("ttl=([0-9]+)").find(line!!)
                        val ttl = ttlMatch?.groups?.get(1)?.value?.toIntOrNull()

                        // Attempt to extract remote IP from the ping line if not already resolved
                        // or if it's different (e.g. if host was a CNAME)
                        var currentPingIp = ipAddress
                        if (line!!.contains("bytes from")) {
                            val ipFromLineMatch = Regex("bytes from ([^:]+):").find(line!!)
                            currentPingIp = ipFromLineMatch?.groups?.get(1)?.value ?: ipAddress
                        }


                        emit(PingResult(
                            ipAddress = currentPingIp,
                            timeTakenMs = time,
                            ttl = ttl,
                            rawOutput = line!!,
                            error = null
                        ))
                    } catch (e: Exception) {
                        // Issue parsing this specific line, emit raw
                        emit(PingResult(ipAddress = ipAddress, rawOutput = line!!, error = "Parse error for line"))
                    }
                } else if (line!!.contains("Request timeout") || line!!.contains("timed out")) {
                     emit(PingResult(ipAddress = ipAddress, rawOutput = line!!, error = "Timeout"))
                }
            }

            val exitCode = process.waitFor()
            val finalOutputString = fullOutput.toString()

            if (exitCode == 0) {
                val parsedResult = parsePingOutput(finalOutputString, ipAddress)
                emit(parsedResult)
            } else {
                // Attempt to parse even if exit code is non-zero, as some info might be available
                val parsedResult = parsePingOutput(finalOutputString, ipAddress)
                if(parsedResult.error == null) {
                     emit(parsedResult.copy(error = "Ping command failed with exit code $exitCode. Output: $finalOutputString"))
                } else {
                     emit(parsedResult.copy(error = "${parsedResult.error}. Ping command failed with exit code $exitCode. Output: $finalOutputString" ))
                }
            }

        } catch (e: Exception) {
            emit(PingResult(ipAddress = ipAddress, rawOutput = "Error executing ping: ${e.message}", error = e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO) // Ensure network operations are on the IO dispatcher

    private fun parsePingOutput(output: String, initialIpAddress: String?): PingResult {
        // More sophisticated parsing logic will be implemented here.
        // This is a placeholder for the example.
        var ipAddress = initialIpAddress
        var packetsTransmitted: Int? = null
        var packetsReceived: Int? = null
        var packetLossRate: Float? = null
        var minRtt: Float? = null
        var avgRtt: Float? = null
        var maxRtt: Float? = null
        var stdDevRtt: Float? = null
        var firstSuccessfulTime: Float? = null
        var firstSuccessfulTtl: Int? = null
        var error: String? = null

        output.lines().forEach { line ->
            if (ipAddress == null && line.startsWith("PING")) {
                // PING google.com (142.250.199.14) 56(84) bytes of data.
                val ipMatch = Regex("\\(([^)]+)\\)").find(line)
                ipAddress = ipMatch?.groups?.get(1)?.value
            }
            if (line.contains("bytes from") && line.contains("time=")) {
                 if (firstSuccessfulTime == null) {
                    val timeMatch = Regex("time=([0-9.]+) ms").find(line)
                    firstSuccessfulTime = timeMatch?.groups?.get(1)?.value?.toFloatOrNull()
                 }
                 if (firstSuccessfulTtl == null) {
                    val ttlMatch = Regex("ttl=([0-9]+)").find(line)
                    firstSuccessfulTtl = ttlMatch?.groups?.get(1)?.value?.toIntOrNull()
                 }
            }
            if (line.contains("packets transmitted")) {
                // 4 packets transmitted, 4 received, 0% packet loss, time 3004ms
                val statsMatch = Regex("(\\d+) packets transmitted, (\\d+) received, (\\d+)% packet loss").find(line)
                if (statsMatch != null) {
                    packetsTransmitted = statsMatch.groups[1]?.value?.toIntOrNull()
                    packetsReceived = statsMatch.groups[2]?.value?.toIntOrNull()
                    packetLossRate = statsMatch.groups[3]?.value?.toFloatOrNull()
                } else {
                    // Handle cases like "100% packet loss" where "received" might be missing before loss percentage.
                    val lossOnlyMatch = Regex("(\\d+) packets transmitted,.*? (\\d+)% packet loss").find(line)
                    if (lossOnlyMatch != null) {
                        packetsTransmitted = lossOnlyMatch.groups[1]?.value?.toIntOrNull()
                        packetsReceived = 0 // If only loss is mentioned, likely 0 received
                        packetLossRate = lossOnlyMatch.groups[2]?.value?.toFloatOrNull()
                    }
                }
            }
            if (line.startsWith("rtt min/avg/max/mdev")) {
                // rtt min/avg/max/mdev = 0.052/0.072/0.092/0.016 ms (Linux)
                // round-trip min/avg/max/stddev = 0.052/0.072/0.092/0.016 ms (macOS)
                val rttMatch = Regex("= (\\d+\\.\\d+)/(\\d+\\.\\d+)/(\\d+\\.\\d+)/(\\d+\\.\\d+) ms").find(line)
                if (rttMatch != null) {
                    minRtt = rttMatch.groups[1]?.value?.toFloatOrNull()
                    avgRtt = rttMatch.groups[2]?.value?.toFloatOrNull()
                    maxRtt = rttMatch.groups[3]?.value?.toFloatOrNull()
                    stdDevRtt = rttMatch.groups[4]?.value?.toFloatOrNull()
                }
            }
            if (line.contains("unknown host", ignoreCase = true) || line.contains("Host not found", ignoreCase = true)) {
                error = "Unknown host"
            }
        }

        // Fallback if detailed stats line not found but individual pings were seen
        if (avgRtt == null && firstSuccessfulTime != null) {
            avgRtt = firstSuccessfulTime
        }


        return PingResult(
            ipAddress = ipAddress,
            timeTakenMs = avgRtt ?: firstSuccessfulTime, // Use avg if available, else first success time
            ttl = firstSuccessfulTtl, // From the first successful reply, can be improved
            packetLossRate = packetLossRate,
            packetsTransmitted = packetsTransmitted,
            packetsReceived = packetsReceived,
            minRtt = minRtt,
            avgRtt = avgRtt,
            maxRtt = maxRtt,
            stdDevRtt = stdDevRtt,
            error = error,
            rawOutput = output
        )
    }
}

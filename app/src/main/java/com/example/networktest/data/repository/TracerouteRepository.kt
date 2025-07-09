package com.example.networktest.data.repository

import com.example.networktest.data.model.TracerouteHop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import javax.inject.Inject

class TracerouteRepository @Inject constructor() {

    // Max hops, default for many traceroute utilities is 30 or 64
    private val defaultMaxHops = 30
    // Timeout for each probe in seconds
    private val defaultTimeoutSeconds = 3 // Lowered from 5 for faster feedback on unresponsive hops

    fun traceroute(host: String, maxHops: Int = defaultMaxHops, timeoutSeconds: Int = defaultTimeoutSeconds): Flow<TracerouteHop> = flow {
        if (host.isBlank()) {
            emit(TracerouteHop(hop = 0, ipAddress = null, error = "Host cannot be empty", rawOutput = "Host cannot be empty"))
            return@flow
        }

        // Basic validation for host
        if (!host.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
            emit(TracerouteHop(hop = 0, ipAddress = null, error = "Invalid host format", rawOutput = "Invalid host format"))
            return@flow
        }

        val command = mutableListOf<String>()
        // Android (Linux) traceroute command
        // traceroute [ -46dFITUnre ] [ -f first_ttl ] [ -g gate,... ] [ -i device ] [ -m max_ttl ] [ -N squeries ] [ -p port ]
        // [ -q nqueries ] [ -s src_addr ] [ -t tos ] [ -w waittime ] [ -z sendwait ] host [ packetlen ]
        command.add("traceroute")
        command.add("-m") // Max TTL (max hops)
        command.add(maxHops.toString())
        command.add("-w") // Wait time for a probe response in seconds.
                          // Note: traceroute uses whole seconds for -w, but some versions might accept fractions.
                          // For simplicity, we'll use whole seconds. The value is effectively a timeout.
        command.add(timeoutSeconds.toString())
        command.add("-q") // Number of probes per hop (usually 3)
        command.add("3") // Setting to 3 probes
        command.add("-n") // Do not resolve IP addresses to hostnames initially (we can do it later if needed or parse if provided)
        command.add(host)

        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            // Example Linux traceroute output line:
            //  1  192.168.1.1 (192.168.1.1)  1.234 ms  1.567 ms  1.890 ms
            //  2  * * *
            //  3  some.host.com (10.0.0.1)  10.123 ms  10.456 ms  10.789 ms
            // Regex might need adjustment based on the exact traceroute version on Android
            // This regex tries to capture common formats.
            val hopPattern = Pattern.compile(
                "^\\s*(\\d+)\\s+" + // Hop number
                "(?:([\\w.-]+)\\s+\\(([\\d.]+)\\)|([\\d.]+)|\\*)" + // Hostname (IP) or IP or *
                "\\s*(?:([\\d.]+\\s+ms)|\\*)" + // RTT1 or *
                "\\s*(?:([\\d.]+\\s+ms)|\\*)" + // RTT2 or *
                "\\s*(?:([\\d.]+\\s+ms)|\\*)?" // RTT3 or * (optional for some tools if less than 3 probes)
            )


            while (reader.readLine().also { line = it } != null) {
                if (line!!.trim().isEmpty() || line!!.startsWith("traceroute to")) {
                    // Skip header or empty lines
                    continue
                }

                val matcher = hopPattern.matcher(line!!)
                if (matcher.matches()) {
                    val hopNum = matcher.group(1)?.toIntOrNull() ?: 0

                    var parsedHostName: String? = matcher.group(2)
                    var parsedIpAddress: String? = matcher.group(3) ?: matcher.group(4)

                    if (parsedIpAddress == "*" || parsedIpAddress == null) { // if IP is asterisk, then hostname would also be (effectively)
                        parsedIpAddress = null
                        parsedHostName = null
                    }


                    val rtt1Str = matcher.group(5)?.replace(" ms", "")
                    val rtt2Str = matcher.group(6)?.replace(" ms", "")
                    val rtt3Str = matcher.group(7)?.replace(" ms", "")

                    val rtt1 = if (rtt1Str == "*") null else rtt1Str?.toFloatOrNull()
                    val rtt2 = if (rtt2Str == "*") null else rtt2Str?.toFloatOrNull()
                    val rtt3 = if (rtt3Str == "*") null else rtt3Str?.toFloatOrNull()

                    var error: String? = null
                    if (parsedIpAddress == null && rtt1 == null && rtt2 == null && rtt3 == null) {
                        error = "Request timed out"
                    }

                    emit(TracerouteHop(
                        hop = hopNum,
                        ipAddress = parsedIpAddress,
                        hostName = parsedHostName,
                        rtt1 = rtt1,
                        rtt2 = rtt2,
                        rtt3 = rtt3,
                        error = error,
                        rawOutput = line!!
                    ))
                } else {
                    // Line didn't match expected hop pattern, could be a summary or error message
                    // For now, emit as a generic hop with raw output if it seems like an error
                    if (line!!.contains("cannot resolve") || line!!.contains("unknown host")) {
                         emit(TracerouteHop(hop = 0, ipAddress = null, error = line, rawOutput = line!!))
                         break // Stop processing on resolution error
                    }
                    // Potentially log non-matching lines for debugging parser: Log.d("TracerouteRepo", "Unparsed line: $line")
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                // Handle non-zero exit code, potentially emitting a final error state
                // Some traceroute versions might output errors to stderr, which is redirected here.
                // We are already parsing line by line, so specific errors might have been caught.
                // This can be a fallback.
                 // val errorOutput = reader.use { it.readText() } // Read remaining output if any
                 // emit(TracerouteHop(hop = 0, ipAddress = null, error = "Traceroute command failed with exit code $exitCode. $errorOutput", rawOutput = "Exit code: $exitCode"))
            }

        } catch (e: Exception) {
            emit(TracerouteHop(hop = 0, ipAddress = null, error = "Error executing traceroute: ${e.message}", rawOutput = "Exception: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}

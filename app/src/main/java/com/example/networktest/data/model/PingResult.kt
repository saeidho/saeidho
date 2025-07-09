package com.example.networktest.data.model

/**
 * Represents the result of a ping operation, either an individual reply or a summary.
 *
 * @property ipAddress The IP address of the host that replied. Can be null if unresolved or for summary lines not specific to an IP.
 * @property timeTakenMs The round-trip time for the ping packet in milliseconds. Null if timeout or error.
 * @property ttl The Time To Live value from the ping reply. Null if not available.
 * @property packetLossRate The packet loss percentage (for summary results). Null for individual pings.
 * @property packetsTransmitted The number of packets transmitted (for summary results). Null for individual pings.
 * @property packetsReceived The number of packets received (for summary results). Null for individual pings.
 * @property minRtt The minimum round-trip time observed (for summary results). Null for individual pings.
 * @property avgRtt The average round-trip time observed (for summary results). Null for individual pings.
 * @property maxRtt The maximum round-trip time observed (for summary results). Null for individual pings.
 * @property stdDevRtt The standard deviation of round-trip times (for summary results). Null for individual pings.
 * @property error An error message if this specific ping reply failed or if the overall operation encountered an issue.
 * @property rawOutput The raw string output from the ping command corresponding to this result.
 * @property isSuccess True if this specific ping result represents a successful reply or a successful summary.
 *                   Based on whether an error is present and if timeTakenMs (for replies) or relevant summary stats are available.
 */
data class PingResult(
    val ipAddress: String? = null,
    val timeTakenMs: Float? = null,
    val ttl: Int? = null,
    val packetLossRate: Float? = null,
    val packetsTransmitted: Int? = null,
    val packetsReceived: Int? = null,
    val minRtt: Float? = null,
    val avgRtt: Float? = null,
    val maxRtt: Float? = null,
    val stdDevRtt: Float? = null,
    val error: String? = null,
    val rawOutput: String,
    val isSuccess: Boolean = error == null && (timeTakenMs != null || (packetsTransmitted != null && avgRtt != null))
)

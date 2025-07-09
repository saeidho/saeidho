package com.example.networktest.data.model

data class TracerouteHop(
    val hop: Int,
    val ipAddress: String?,
    val hostName: String? = null, // Resolved hostname if available
    val rtt1: Float? = null,
    val rtt2: Float? = null,
    val rtt3: Float? = null,
    val error: String? = null, // For errors specific to this hop (e.g., timeout *)
    val rawOutput: String // Raw line for this hop
) {
    val avgRtt: Float?
        get() {
            val rtts = listOfNotNull(rtt1, rtt2, rtt3)
            return if (rtts.isNotEmpty()) rtts.average().toFloat() else null
        }

    val displayAddress: String
        get() = hostName ?: ipAddress ?: "* * *"
}

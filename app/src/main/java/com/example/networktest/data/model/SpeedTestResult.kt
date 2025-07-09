package com.example.networktest.data.model

data class SpeedTestResult(
    val downloadSpeedMbps: Double? = null,
    val uploadSpeedMbps: Double? = null, // Will be null in this basic implementation
    val pingTimeMs: Long? = null, // Could be added by pinging the test server first
    val serverLocation: String? = null, // If known
    val error: String? = null,
    val progressPercent: Int = 0, // For UI updates during the test
    val isTesting: Boolean = false
)

package com.example.networktest.data.model

data class PortScanResult(
    val port: Int,
    val isOpen: Boolean,
    val banner: String? = null, // Optional: for banner grabbing if implemented
    val error: String? = null // If there was an error scanning this specific port
)

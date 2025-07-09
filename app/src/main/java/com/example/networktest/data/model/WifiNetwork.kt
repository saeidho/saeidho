package com.example.networktest.data.model

data class WifiNetwork(
    val ssid: String, // Service Set Identifier (Network Name)
    val bssid: String, // Basic Service Set Identifier (MAC Address of AP)
    val signalStrengthDbm: Int, // Received Signal Strength Indicator in dBm
    val signalLevel: Int, // Signal strength as a level (0-4 or 0-100, depends on calculation)
    val frequency: Int, // Frequency in MHz
    val channel: Int, // Wi-Fi channel number
    val securityType: String, // e.g., WPA2, WPA3, WEP, Open
    val capabilities: String, // Raw capabilities string
    val isConnected: Boolean = false // Whether the device is currently connected to this network
) {
    companion object {
        // Helper to calculate signal level from RSSI (common Android way)
        // Returns a value from 0 to 4 (inclusive)
        fun calculateSignalLevel(rssi: Int, numLevels: Int = 5): Int {
            return when {
                rssi >= -55 -> numLevels - 1 // Excellent
                rssi >= -65 -> numLevels - 2 // Good
                rssi >= -75 -> numLevels - 3 // Fair
                rssi >= -85 -> numLevels - 4 // Weak
                else -> 0 // Very weak / Unusable
            }
        }

        fun getChannelFromFrequency(frequency: Int): Int {
            return when (frequency) {
                // 2.4 GHz band
                2412 -> 1
                2417 -> 2
                2422 -> 3
                2427 -> 4
                2432 -> 5
                2437 -> 6
                2442 -> 7
                2447 -> 8
                2452 -> 9
                2457 -> 10
                2462 -> 11
                2467 -> 12
                2472 -> 13
                2484 -> 14 // Japan only for 802.11b

                // 5 GHz band (common channels, not exhaustive)
                // U-NII-1
                5180 -> 36
                5200 -> 40
                5220 -> 44
                5240 -> 48
                // U-NII-2A
                5260 -> 52
                5280 -> 56
                5300 -> 60
                5320 -> 64
                // U-NII-2C
                5500 -> 100
                5520 -> 104
                5540 -> 108
                5560 -> 112
                5580 -> 116
                5600 -> 120
                5620 -> 124
                5640 -> 128
                5660 -> 132
                5680 -> 136
                5700 -> 140
                5720 -> 144 // Overlaps with ISM band for some countries
                // U-NII-3 / ISM
                5745 -> 149
                5765 -> 153
                5785 -> 157
                5805 -> 161
                5825 -> 165

                // 6 GHz (Wi-Fi 6E) - PSC channels
                // These are just examples, a full list is very long.
                // The channel number is (frequency - 5950) / 5 + 1, for frequencies starting 5955 MHz.
                // For 5925 + 20 * (N-1) rule:
                5955 -> 1 // (6 GHz band)
                5975 -> 5
                5995 -> 9
                // ... many more ...
                7115 -> 233


                else -> {
                    // Fallback calculation for 5GHz/6GHz if not in map
                    if (frequency in 5000..5900) { // Approximate 5 GHz
                        (frequency - 5000) / 5
                    } else if (frequency > 5925) { // Approximate 6 GHz (Wi-Fi 6E)
                        // Simplified: The actual channel numbering for 6GHz is more complex and region-dependent.
                        // Often, for 6GHz, the primary channel center frequency is used.
                        // A common way to refer to 6GHz channels is by their "number" in sequence,
                        // e.g., Channel 1, 5, 9, etc., corresponding to specific frequencies.
                        // This is a placeholder and might need more accurate mapping for 6E.
                        // (frequency - 5950) / 5 + 1 might be one scheme for 20MHz channels starting at 5955MHz
                         if (frequency % 5 == 0 && frequency >= 5955) {
                            (frequency - 5955)/5 + 1
                        } else {
                            0 // Unknown or non-standard
                        }
                    }
                    else -> 0 // Unknown or non-standard
                }
            }
        }

        fun getSecurityType(capabilities: String): String {
            return when {
                capabilities.contains("WPA3") -> "WPA3" // Check for WPA3 first
                capabilities.contains("WPA2-PSK") -> "WPA2-PSK"
                capabilities.contains("WPA2-EAP") -> "WPA2-EAP"
                capabilities.contains("WPA2") -> "WPA2" // Generic WPA2
                capabilities.contains("WPA-PSK") -> "WPA-PSK"
                capabilities.contains("WPA-EAP") -> "WPA-EAP"
                capabilities.contains("WPA") -> "WPA" // Generic WPA
                capabilities.contains("WEP") -> "WEP"
                capabilities.contains("ESS") && !capabilities.contains("WPA") && !capabilities.contains("WEP") -> "Open" // Basic Service Set
                else -> if (capabilities.isBlank() || capabilities == "[ESS]") "Open" else "Unknown" // Or Open if just ESS
            }
        }
    }
}

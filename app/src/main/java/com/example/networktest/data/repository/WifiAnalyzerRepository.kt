package com.example.networktest.data.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.networktest.data.model.WifiNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class WifiAnalyzerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getWifiNetworks(): Flow<List<WifiNetwork>> = callbackFlow {
        // Check for permissions first
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED // For starting scan
        ) {
            close(IllegalStateException("Missing Wi-Fi or Location permissions. Please grant ACCESS_FINE_LOCATION, ACCESS_WIFI_STATE, and CHANGE_WIFI_STATE."))
            return@callbackFlow
        }

        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent.action) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    if (success) {
                        trySend(processScanResults())
                    } else {
                        // Scan failed, potentially send an empty list or error state
                        trySend(emptyList()) // Or handle error appropriately
                    }
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        // Initial scan
        startScan()


        awaitClose {
            context.unregisterReceiver(wifiScanReceiver)
        }
    }.flowOn(Dispatchers.IO) // Perform registration and initial scan off the main thread


    fun startScan(): Boolean {
         if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
             ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
             ActivityCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            // Permissions are not granted.
            // The Flow will close with an exception, or you can handle this by emitting an error state.
            return false
        }

        // For Android Q (API 29) and above, apps cannot trigger Wi-Fi scans as frequently.
        // The system may throttle scan requests.
        // WifiManager.startScan() is deprecated in API 28.
        // However, it's still the primary way to request a scan.
        // The system handles throttling. If the app calls startScan() too often, it might return false or do nothing.
        return wifiManager.startScan()
    }


    @Suppress("DEPRECATION") // For ScanResult.SSID on older APIs if not using WifiInfo.ssid
    private fun processScanResults(): List<WifiNetwork> {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // This should ideally be checked before even registering the receiver or calling processScanResults.
            // If we reach here without permission, something is wrong.
            return emptyList() // Or throw an exception / emit error state
        }
        val results: List<ScanResult> = wifiManager.scanResults
        val currentConnection = wifiManager.connectionInfo

        return results.mapNotNull { scanResult ->
            // Skip empty SSIDs as they are often hidden or problematic
            if (scanResult.SSID.isNullOrEmpty()) {
                return@mapNotNull null
            }

            val frequency = scanResult.frequency
            val channel = WifiNetwork.getChannelFromFrequency(frequency)
            val signalLevel = WifiNetwork.calculateSignalLevel(scanResult.level)
            val security = WifiNetwork.getSecurityType(scanResult.capabilities)

            // On Android S (API 31) and above, SSID might be "<unknown ssid>" if location is off
            // or if the app doesn't have precise location.
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scanResult.SSID == "<unknown ssid>") {
                 "Unknown SSID (Location Disabled?)"
            } else {
                scanResult.SSID
            }


            WifiNetwork(
                ssid = ssid,
                bssid = scanResult.BSSID,
                signalStrengthDbm = scanResult.level,
                signalLevel = signalLevel,
                frequency = frequency,
                channel = channel,
                securityType = security,
                capabilities = scanResult.capabilities,
                isConnected = currentConnection?.bssid == scanResult.BSSID && currentConnection?.ssid?.removeSurrounding("\"") == scanResult.SSID
            )
        }.sortedByDescending { it.signalStrengthDbm } // Sort by strongest signal first
    }
}

package com.jtexpress.bevest.data.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class NearbyWifiNetwork(
    val ssid: String,
    val rssi: Int,
    val is24Ghz: Boolean,
    val isSecured: Boolean,
)

data class WifiScanState(
    val scanning: Boolean = false,
    val networks: List<NearbyWifiNetwork> = emptyList(),
    val statusMessage: String? = null,
)

/**
 * Scans nearby Wi-Fi networks via Android's [WifiManager] so the user can pick a nearby
 * 2.4 GHz SSID instead of typing it manually when provisioning or updating a BeVest ESP32.
 */
@Singleton
class NearbyWifiScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _state = MutableStateFlow(WifiScanState())
    val state: StateFlow<WifiScanState> = _state.asStateFlow()

    private var activeReceiver: BroadcastReceiver? = null
    private var timeoutJob: Job? = null

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        val wm = wifiManager
        if (wm == null) {
            _state.update {
                it.copy(scanning = false, statusMessage = "Wi-Fi scanning is not supported on this device.")
            }
            return
        }

        if (!hasLocationPermission()) {
            _state.update {
                it.copy(
                    scanning = false,
                    statusMessage = "Location permission is required to scan nearby Wi-Fi networks.",
                )
            }
            return
        }

        if (!wm.isWifiEnabled) {
            _state.update {
                it.copy(
                    scanning = false,
                    networks = emptyList(),
                    statusMessage = "Turn on Wi-Fi on your phone to see nearby networks.",
                )
            }
            return
        }

        if (!isLocationEnabled()) {
            _state.update {
                it.copy(
                    scanning = false,
                    statusMessage = "Turn on Location in quick settings so Android can list nearby Wi-Fi SSIDs.",
                )
            }
            return
        }

        // Immediately publish cached scan results so the user sees networks right away
        val cached = extractNetworks(runCatching { wm.scanResults }.getOrNull().orEmpty())
        _state.update {
            it.copy(
                scanning = true,
                networks = if (cached.isNotEmpty()) cached else it.networks,
                statusMessage = if (cached.isEmpty()) "Scanning for nearby 2.4 GHz Wi-Fi networks..." else null,
            )
        }

        unregisterReceiver()
        timeoutJob?.cancel()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val results = extractNetworks(runCatching { wm.scanResults }.getOrNull().orEmpty())
                finishScan(results)
            }
        }
        activeReceiver = receiver

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }

        @Suppress("DEPRECATION")
        val started = runCatching { wm.startScan() }.getOrDefault(false)
        if (!started) {
            // Android throttles startScan() to 4 calls per 2 minutes — cached results are still valid
            finishScan(cached)
            return
        }

        timeoutJob = scope.launch {
            delay(5_000L)
            val latest = extractNetworks(runCatching { wm.scanResults }.getOrNull().orEmpty())
            finishScan(latest)
        }
    }

    fun stop() {
        timeoutJob?.cancel()
        unregisterReceiver()
        _state.update { it.copy(scanning = false) }
    }

    private fun finishScan(results: List<NearbyWifiNetwork>) {
        timeoutJob?.cancel()
        unregisterReceiver()
        _state.update {
            it.copy(
                scanning = false,
                networks = results,
                statusMessage = if (results.isEmpty()) {
                    "No nearby Wi-Fi networks found. You can still type the 2.4 GHz SSID manually."
                } else {
                    null
                },
            )
        }
    }

    private fun unregisterReceiver() {
        val r = activeReceiver ?: return
        activeReceiver = null
        runCatching { context.unregisterReceiver(r) }
    }

    @Suppress("DEPRECATION")
    private fun extractNetworks(raw: List<ScanResult>): List<NearbyWifiNetwork> {
        val bySsid = linkedMapOf<String, NearbyWifiNetwork>()
        for (sr in raw) {
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sr.wifiSsid?.toString()?.trim()?.removeSurrounding("\"")
            } else {
                sr.SSID?.trim()
            }?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: continue

            val is24 = sr.frequency in 2400..2500
            val caps = sr.capabilities.orEmpty().uppercase()
            val secured = caps.contains("WPA") || caps.contains("WEP") || caps.contains("EAP") || caps.contains("SAE")
            val candidate = NearbyWifiNetwork(
                ssid = ssid,
                rssi = sr.level,
                is24Ghz = is24,
                isSecured = secured,
            )

            val prev = bySsid[ssid]
            if (prev == null) {
                bySsid[ssid] = candidate
            } else {
                // For dual-band routers, prefer the 2.4 GHz entry (required by ESP32) with strongest RSSI
                val preferCandidate = when {
                    candidate.is24Ghz && !prev.is24Ghz -> true
                    !candidate.is24Ghz && prev.is24Ghz -> false
                    else -> candidate.rssi > prev.rssi
                }
                if (preferCandidate) {
                    bySsid[ssid] = candidate
                }
            }
        }
        return bySsid.values.sortedWith(
            compareByDescending<NearbyWifiNetwork> { it.is24Ghz }
                .thenByDescending { it.rssi },
        )
    }
}

package com.jtexpress.bevest.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredBleVest(
    val name: String,
    val address: String,
    val cleanMac: String,
    val rssi: Int,
    val device: BluetoothDevice? = null,
)

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    TRANSMITTING_WIFI,
    SUCCESS_CONFIRMED,
    FAILED
}

data class BleProvisionerState(
    val scanning: Boolean = false,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val discoveredVests: List<DiscoveredBleVest> = emptyList(),
    val connectedVest: DiscoveredBleVest? = null,
    val statusMessage: String? = null,
    val errorCode: String? = null,
)

@Singleton
class BleProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CONFIG_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val STATUS_CHAR_UUID: UUID = UUID.fromString("1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BleProvisionerState())
    val state: StateFlow<BleProvisionerState> = _state.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var scanJob: Job? = null
    private var wifiVerifyJob: Job? = null
    private var lastAttemptedSsid: String = ""
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val isLocationEnabled: Boolean
        get() {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            return locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                   locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        }

    fun hasPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted && fineLocationGranted
        } else {
            fineLocationGranted
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val dev = result.device ?: return

            val cleanMac = dev.address.replace(Regex("[^A-Fa-f0-9]"), "").uppercase()

            val rawName = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                ?: (try { dev.name } catch (_: SecurityException) { null })?.takeIf { it.isNotBlank() }
                ?: ""

            // Must match BEVEST pattern or prefix, or advertise the BeVest GATT Service UUID
            val matchesName = rawName.startsWith("BEVEST", ignoreCase = true) || rawName.contains("BEVEST", ignoreCase = true)
            val matchesService = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true

            if (matchesName || matchesService) {
                val resolvedName = if (rawName.isNotBlank() && rawName.contains("BEVEST", ignoreCase = true)) {
                    rawName
                } else {
                    "BEVEST-$cleanMac"
                }

                val item = DiscoveredBleVest(
                    name = resolvedName,
                    address = dev.address,
                    cleanMac = cleanMac,
                    rssi = result.rssi,
                    device = dev
                )

                _state.update { current ->
                    val list = current.discoveredVests.toMutableList()
                    val existingIdx = list.indexOfFirst { it.address.equals(item.address, ignoreCase = true) }
                    if (existingIdx >= 0) {
                        val existing = list[existingIdx]
                        list[existingIdx] = item.copy(
                            name = if (item.name.isNotBlank()) item.name else existing.name,
                            rssi = item.rssi
                        )
                    } else {
                        list.add(item)
                    }
                    current.copy(discoveredVests = list)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    scanning = false,
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "BLE Scan failed with error code $errorCode"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (activeGatt != null) {
            disconnect()
        }
        if (!hasPermissions()) {
            _state.update {
                it.copy(
                    scanning = false,
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Bluetooth Scan permission required. Please grant permission."
                )
            }
            return
        }

        if (!isBluetoothEnabled) {
            _state.update {
                it.copy(
                    scanning = false,
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Bluetooth is turned OFF. Please enable Bluetooth on your phone."
                )
            }
            return
        }

        if (!isLocationEnabled) {
            _state.update {
                it.copy(
                    scanning = false,
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Location is turned OFF. Please turn on Location in quick settings to scan for vests."
                )
            }
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.update {
                it.copy(
                    scanning = false,
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Bluetooth LE Scanner not supported on this device."
                )
            }
            return
        }

        _state.update {
            it.copy(
                scanning = true,
                connectionState = BleConnectionState.SCANNING,
                statusMessage = "Searching for nearby Bevest vest signals...",
                discoveredVests = emptyList()
            )
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val filters = listOf(ScanFilter.Builder().build())
            scanner.startScan(filters, settings, scanCallback)

            // Keep scanning continuously without a premature time limit while the pairing UI is open
            scanJob?.cancel()
        } catch (e: SecurityException) {
            _state.update {
                it.copy(
                    scanning = false,
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Bluetooth permission not granted: ${e.message}"
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(scanning = false, statusMessage = "Could not start BLE scanner: ${e.message}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        _state.update { it.copy(scanning = false) }
    }

    @SuppressLint("MissingPermission")
    fun connectToVest(vest: DiscoveredBleVest) {
        stopScan()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Bluetooth Connect permission required. Please allow permission in settings."
                )
            }
            return
        }

        _state.update {
            it.copy(
                connectedVest = vest,
                connectionState = BleConnectionState.CONNECTING,
                statusMessage = "Connecting to ${vest.name}..."
            )
        }

        if (vest.device == null) {
            // Simulated connection for environments without physical BLE adapter
            scope.launch {
                delay(1000)
                _state.update {
                    it.copy(
                        connectionState = BleConnectionState.CONNECTED,
                        statusMessage = "Connected to ${vest.name}!"
                    )
                }
            }
            return
        }

        try {
            activeGatt?.close()
            activeGatt = vest.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.FAILED,
                    statusMessage = "Connection failed: ${e.message}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {}
            scope.launch {
                delay(300)
                try {
                    gatt.close()
                } catch (_: Exception) {}
            }
        }
        _state.update {
            it.copy(
                connectedVest = null,
                connectionState = BleConnectionState.DISCONNECTED,
                statusMessage = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun sendWifiConfig(ssid: String, pass: String, vestId: String) {
        val trimmedSsid = ssid.trim()
        val trimmedPass = pass.trim()

        if (trimmedSsid.isBlank()) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.FAILED,
                    errorCode = "WIFI_INVALID",
                    statusMessage = "Wi-Fi SSID is required. Please enter the 2.4 GHz network name."
                )
            }
            return
        }
        if (trimmedSsid.length > 32) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.FAILED,
                    errorCode = "WIFI_INVALID",
                    statusMessage = "Invalid Wi-Fi SSID — network name cannot exceed 32 characters."
                )
            }
            return
        }
        if (trimmedPass.isBlank() || trimmedPass.length < 8 || trimmedPass.length > 63) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.FAILED,
                    errorCode = "WIFI_INVALID",
                    statusMessage = "Invalid Wi-Fi password — WPA/WPA2 passwords must be between 8 and 63 characters."
                )
            }
            return
        }

        lastAttemptedSsid = trimmedSsid
        val gatt = activeGatt
        val vest = _state.value.connectedVest

        _state.update {
            it.copy(
                connectionState = BleConnectionState.TRANSMITTING_WIFI,
                errorCode = null,
                statusMessage = "Transmitting Wi-Fi credentials to ${vest?.name ?: "Vest"} & verifying connection..."
            )
        }

        if (gatt == null) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.FAILED,
                    errorCode = "BLE_NOT_CONNECTED",
                    statusMessage = "Not connected to vest over Bluetooth. Please connect to the vest first."
                )
            }
            return
        }

        wifiVerifyJob?.cancel()
        scope.launch {
            var service = gatt.getService(SERVICE_UUID)
            var configChar = service?.getCharacteristic(CONFIG_CHAR_UUID)

            if (configChar == null) {
                android.util.Log.w("BleProvisioner", "Config characteristic null, discovering services...")
                gatt.discoverServices()
                delay(500)
                service = gatt.getService(SERVICE_UUID)
                configChar = service?.getCharacteristic(CONFIG_CHAR_UUID)
            }

            if (configChar == null) {
                _state.update {
                    it.copy(
                        connectionState = BleConnectionState.FAILED,
                        errorCode = "GATT_SERVICE_MISSING",
                        statusMessage = "Vest GATT provisioning service not found. Please reconnect to the vest."
                    )
                }
                return@launch
            }

            val escapedSsid = trimmedSsid.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedPass = trimmedPass.replace("\\", "\\\\").replace("\"", "\\\"")
            val payload = """{"ssid":"$escapedSsid","pass":"$escapedPass","vestId":"$vestId"}"""
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            val writeType = if ((configChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            var success = false
            for (attempt in 1..3) {
                success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(configChar, payloadBytes, writeType) == 0
                } else {
                    @Suppress("DEPRECATION")
                    configChar.value = payloadBytes
                    @Suppress("DEPRECATION")
                    configChar.writeType = writeType
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(configChar)
                }

                if (success) {
                    android.util.Log.d("BleProvisioner", "Wi-Fi config written successfully on attempt $attempt")
                    break
                } else {
                    android.util.Log.w("BleProvisioner", "Write attempt $attempt returned false, retrying in 250ms...")
                    delay(250)
                }
            }

            if (!success) {
                _state.update {
                    it.copy(
                        connectionState = BleConnectionState.FAILED,
                        errorCode = "WRITE_FAILED",
                        statusMessage = "Failed to transmit Wi-Fi credentials to vest over Bluetooth."
                    )
                }
                return@launch
            }

            // Actively poll status characteristic & enforce a verification timeout so invalid Wi-Fi credentials are always caught
            wifiVerifyJob = scope.launch {
                val statusChar = service?.getCharacteristic(STATUS_CHAR_UUID)
                repeat(9) {
                    delay(2_000)
                    val curState = _state.value.connectionState
                    if (curState != BleConnectionState.TRANSMITTING_WIFI) return@launch
                    if (statusChar != null && activeGatt != null) {
                        try {
                            activeGatt?.readCharacteristic(statusChar)
                        } catch (_: Exception) {}
                    }
                }
                if (_state.value.connectionState == BleConnectionState.TRANSMITTING_WIFI) {
                    _state.update {
                        it.copy(
                            connectionState = BleConnectionState.FAILED,
                            errorCode = "WIFI_INVALID",
                            statusMessage = "Invalid Wi-Fi credentials — vest could not connect to \"$trimmedSsid\". Please check the Wi-Fi SSID (2.4 GHz) and password."
                        )
                    }
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.update {
                    it.copy(
                        connectionState = BleConnectionState.CONNECTED,
                        statusMessage = "Connected to vest! Discovering services..."
                    )
                }
                scope.launch {
                    delay(300)
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    delay(200)
                    gatt?.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try {
                    gatt?.close()
                } catch (_: Exception) {}
                if (activeGatt == gatt) {
                    activeGatt = null
                }
                _state.update { current ->
                    when (current.connectionState) {
                        BleConnectionState.SUCCESS_CONFIRMED -> current
                        BleConnectionState.FAILED -> current.copy(connectedVest = null)
                        BleConnectionState.TRANSMITTING_WIFI -> current.copy(
                            connectedVest = null,
                            connectionState = BleConnectionState.FAILED,
                            errorCode = "WIFI_INVALID",
                            statusMessage = "Could not verify connection to \"${lastAttemptedSsid.ifBlank { "Wi-Fi" }}\". Please verify the Wi-Fi SSID (2.4 GHz) and password, then reconnect."
                        )
                        else -> current.copy(
                            connectedVest = null,
                            connectionState = BleConnectionState.DISCONNECTED,
                            statusMessage = "Vest disconnected."
                        )
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                // Request 512-byte MTU so Wi-Fi JSON payload transmits cleanly in a single frame
                gatt.requestMtu(512)

                val service = gatt.getService(SERVICE_UUID)
                val statusChar = service?.getCharacteristic(STATUS_CHAR_UUID)
                if (statusChar != null) {
                    gatt.setCharacteristicNotification(statusChar, true)
                    val descriptor = statusChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
                _state.update {
                    it.copy(
                        connectionState = BleConnectionState.CONNECTED,
                        statusMessage = "Ready for QR verification and Wi-Fi provisioning."
                    )
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            android.util.Log.d("BleProvisioner", "MTU negotiated: $mtu (status: $status)")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            android.util.Log.d("BleProvisioner", "Descriptor write status: $status for ${descriptor?.uuid}")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            android.util.Log.d("BleProvisioner", "Characteristic write status: $status for ${characteristic?.uuid}")
        }

        private fun handleStatusBytes(rawBytes: ByteArray?) {
            val strVal = rawBytes?.toString(Charsets.UTF_8)?.trim()?.toIntOrNull()
            val value = strVal ?: rawBytes?.firstOrNull()?.toInt() ?: 0
            when (value) {
                1 -> _state.update {
                    it.copy(
                        errorCode = null,
                        statusMessage = "Vest is testing connection to \"${lastAttemptedSsid.ifBlank { "Wi-Fi" }}\"..."
                    )
                }
                2 -> {
                    wifiVerifyJob?.cancel()
                    _state.update {
                        it.copy(
                            connectionState = BleConnectionState.SUCCESS_CONFIRMED,
                            errorCode = null,
                            statusMessage = "✓ Vest confirmed connection to \"${lastAttemptedSsid.ifBlank { "Wi-Fi" }}\" and switched to live stream!"
                        )
                    }
                }
                3 -> {
                    wifiVerifyJob?.cancel()
                    _state.update {
                        it.copy(
                            connectionState = BleConnectionState.FAILED,
                            errorCode = "WIFI_INVALID",
                            statusMessage = "Invalid Wi-Fi credentials — authentication failed for \"${lastAttemptedSsid.ifBlank { "Wi-Fi" }}\". Please check the Wi-Fi password and try again."
                        )
                    }
                }
                4 -> {
                    wifiVerifyJob?.cancel()
                    _state.update {
                        it.copy(
                            connectionState = BleConnectionState.FAILED,
                            errorCode = "WIFI_NOT_FOUND",
                            statusMessage = "Invalid Wi-Fi SSID — network \"${lastAttemptedSsid.ifBlank { "Wi-Fi" }}\" was not found. Ensure the SSID is spelled accurately and is a 2.4 GHz network."
                        )
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == STATUS_CHAR_UUID) {
                handleStatusBytes(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == STATUS_CHAR_UUID) {
                @Suppress("DEPRECATION")
                handleStatusBytes(characteristic.value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == STATUS_CHAR_UUID) {
                handleStatusBytes(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.uuid == STATUS_CHAR_UUID) {
                @Suppress("DEPRECATION")
                handleStatusBytes(characteristic.value)
            }
        }
    }
}


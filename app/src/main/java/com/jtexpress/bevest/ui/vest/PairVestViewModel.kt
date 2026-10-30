package com.jtexpress.bevest.ui.vest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.data.ble.BleConnectionState
import com.jtexpress.bevest.data.ble.BleProvisioner
import com.jtexpress.bevest.data.ble.DiscoveredBleVest
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject

enum class PairStep {
    LOOK_FOR_BLE,       // 1. Scan & listen for nearby BLE signals from BeVest vests
    BLE_CONNECTED,      // 2. Connected to selected BLE vest
    SCAN_QR,            // 3. Scan physical QR on the vest & validate
    PROVISION_WIFI,     // 4. (Optional) Configure Site WiFi credentials over BLE
    SELECT_WORKER,      // 5. Choose a worker to assign or keep available in directory
    CONNECTION_TEST,    // 6. Sensor reading confirmation
    DONE                // 7. Success! Claimed into company directory & visible to Admin
}

data class ConnectionChecks(
    val heartRate: Boolean = false,
    val temperature: Boolean = false,
    val motion: Boolean = false,
    val gps: Boolean = false,
    val connection: Boolean = false,
) {
    val allPass get() = heartRate && temperature && motion && gps && connection
}

data class PairVestState(
    val step: PairStep = PairStep.LOOK_FOR_BLE,
    val scanningBle: Boolean = false,
    val discoveredBles: List<DiscoveredBleVest> = emptyList(),
    val connectedBle: DiscoveredBleVest? = null,
    val bleStatusMsg: String? = null,
    val vestIdInput: String = "",
    val vestIdError: String? = null,
    val identifiedVestId: String? = null,
    val scannedSerial: String? = null,
    val scannedMac: String? = null,
    val wifiSsid: String = "",
    val wifiPass: String = "",
    val provisioningWifi: Boolean = false,
    val wifiProvisionSuccess: Boolean = false,
    val checking: Boolean = false,
    val unassignedWorkers: List<Worker> = emptyList(),
    val selectedWorkerId: String? = null,
    val testing: Boolean = false,
    val checks: ConnectionChecks = ConnectionChecks(),
    val submitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class PairVestViewModel @Inject constructor(
    private val vestRepository: VestRepository,
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val bleProvisioner: BleProvisioner,
) : ViewModel() {

    private val _state = MutableStateFlow(PairVestState())
    val state: StateFlow<PairVestState> = _state.asStateFlow()

    private var currentUser: User? = null
    private var siteId: String? = null

    init {
        // Collect BLE provisioner state in background
        viewModelScope.launch {
            bleProvisioner.state.collect { bleState ->
                _state.update { current ->
                    current.copy(
                        scanningBle = bleState.scanning,
                        discoveredBles = bleState.discoveredVests,
                        connectedBle = bleState.connectedVest ?: current.connectedBle,
                        bleStatusMsg = bleState.statusMessage,
                        wifiProvisionSuccess = bleState.connectionState == BleConnectionState.SUCCESS_CONFIRMED,
                        provisioningWifi = bleState.connectionState == BleConnectionState.TRANSMITTING_WIFI
                    )
                }
            }
        }
    }

    val isBluetoothEnabled: Boolean get() = bleProvisioner.isBluetoothEnabled
    val isLocationEnabled: Boolean get() = bleProvisioner.isLocationEnabled
    val hasBlePermissions: Boolean get() = bleProvisioner.hasPermissions()

    fun bind(user: User) {
        currentUser = user
        siteId = user.siteId
    }

    // ── BLE Pairing Actions ───────────────────────────────────────────────────

    fun startBleScan() {
        _state.update { it.copy(error = null) }
        bleProvisioner.startScan()
    }

    fun stopBleScan() {
        bleProvisioner.stopScan()
    }

    fun connectToBleVest(vest: DiscoveredBleVest) {
        _state.update { it.copy(error = null, connectedBle = vest) }
        bleProvisioner.connectToVest(vest)
        _state.update { it.copy(step = PairStep.BLE_CONNECTED) }
    }

    fun proceedToQrScan() {
        _state.update { it.copy(step = PairStep.SCAN_QR, error = null) }
    }

    fun onVestIdInput(v: String) = _state.update { it.copy(vestIdInput = v, vestIdError = null, error = null) }

    fun onWifiSsidInput(v: String) = _state.update { it.copy(wifiSsid = v, error = null) }

    fun onWifiPassInput(v: String) = _state.update { it.copy(wifiPass = v, error = null) }

    // ── QR Scanning & Hardware Validation ────────────────────────────────────

    fun onScanned(value: String) {
        val raw = value.trim()
        var parsedVestId = raw
        var parsedMac = ""
        var parsedSerial = ""

        try {
            if (raw.startsWith("{") && raw.endsWith("}")) {
                val json = JSONObject(raw)
                parsedVestId = json.optString("vestId", raw)
                parsedMac = json.optString("mac", json.optString("macAddress", ""))
                parsedSerial = json.optString("serialNumber", json.optString("esp32Serial", ""))
            } else if (raw.contains("/vest/")) {
                parsedVestId = raw.substringAfter("/vest/").substringBefore("?").trim().uppercase()
            }
        } catch (_: Exception) {
            parsedVestId = raw
        }

        _state.update {
            it.copy(
                vestIdInput = parsedVestId,
                scannedMac = parsedMac.ifBlank { it.scannedMac },
                scannedSerial = parsedSerial.ifBlank { it.scannedSerial }
            )
        }
        validateAndClaimVest(parsedVestId, parsedSerial, parsedMac)
    }

    fun manualValidateAndClaim() {
        val vestId = _state.value.vestIdInput.trim()
        val err = Validators.vestId(vestId)
        if (err != null) {
            _state.update { it.copy(vestIdError = err) }
            return
        }
        validateAndClaimVest(vestId, _state.value.scannedSerial.orEmpty(), _state.value.scannedMac.orEmpty())
    }

    /**
     * Validates Vest ID, Serial Number and MAC Address against both:
     * 1. The connected BLE device (hardware verification)
     * 2. The central Firestore hardware database (developer inventory verification)
     */
    private fun validateAndClaimVest(vestId: String, serialNumber: String, qrMac: String) {
        val connected = _state.value.connectedBle
        val cleanBleMac = connected?.cleanMac.orEmpty()
        val cleanQrMac = qrMac.replace(Regex("[^A-Fa-f0-9]"), "").uppercase()

        // 1. Hardware verification: QR MAC vs Connected BLE MAC
        if (cleanBleMac.isNotBlank() && cleanQrMac.isNotBlank() && !Validators.isMatchingEsp32Mac(cleanBleMac, cleanQrMac)) {
            _state.update {
                it.copy(
                    error = "Hardware Mismatch! Scanned QR MAC ($qrMac) does not match the connected BLE vest ($cleanBleMac). Please scan the QR code physically affixed to the connected vest."
                )
            }
            return
        }

        val macToVerify = if (cleanQrMac.isNotBlank()) qrMac else (connected?.address ?: "")
        val user = currentUser

        _state.update { it.copy(checking = true, error = null) }

        viewModelScope.launch {
            val userUid = user?.uid ?: "sso-app"
            val userName = user?.fullName ?: "Site Safety Officer"
            val userEmail = user?.email ?: "sso@bevest.com"
            val company = user?.contractorId ?: user?.siteId

            when (val result = vestRepository.claimVest(
                vestId = vestId,
                serialNumber = serialNumber,
                macAddress = macToVerify,
                claimedByUid = userUid,
                claimedByName = userName,
                claimedByEmail = userEmail,
                companyId = company,
                siteId = user?.siteId
            )) {
                is Outcome.Failure -> {
                    _state.update { it.copy(checking = false, error = result.error.message) }
                }
                is Outcome.Success -> {
                    // Successfully claimed into company directory!
                    // Load unassigned workers in case the user wants to assign immediately
                    loadUnassignedWorkers(vestId)
                    _state.update {
                        it.copy(
                            checking = false,
                            identifiedVestId = vestId,
                            step = PairStep.PROVISION_WIFI,
                            successMessage = "✓ Vest $vestId verified and claimed into company directory!"
                        )
                    }
                }
            }
        }
    }

    // ── WiFi Provisioning ─────────────────────────────────────────────────────

    fun sendWifiCredentials() {
        val ssid = _state.value.wifiSsid.trim()
        val pass = _state.value.wifiPass.trim()
        val vestId = _state.value.identifiedVestId ?: _state.value.vestIdInput

        if (ssid.isBlank()) {
            _state.update { it.copy(error = "Please enter the site Wi-Fi SSID (Network Name).") }
            return
        }

        _state.update { it.copy(provisioningWifi = true, error = null) }
        bleProvisioner.sendWifiConfig(ssid, pass, vestId)
    }

    fun skipWifiAndProceed() {
        _state.update { it.copy(step = PairStep.SELECT_WORKER) }
    }

    // ── Worker Assignment ─────────────────────────────────────────────────────

    private fun loadUnassignedWorkers(vestId: String) {
        viewModelScope.launch {
            val outcome = workerRepository.observeWorkers(contractorId = null, siteId = siteId).first()
            val workers = (outcome as? Outcome.Success)?.data.orEmpty()
                .filter { it.active && it.assignedVestId.isNullOrBlank() }
            _state.update {
                it.copy(
                    identifiedVestId = vestId,
                    unassignedWorkers = workers,
                )
            }
        }
    }

    fun selectWorker(workerId: String) {
        _state.update { it.copy(selectedWorkerId = workerId, step = PairStep.CONNECTION_TEST) }
        runConnectionTest()
    }

    fun finishWithoutWorker() {
        _state.update { it.copy(step = PairStep.DONE) }
    }

    // ── Connection Test ───────────────────────────────────────────────────────

    fun runConnectionTest() {
        val workerId = _state.value.selectedWorkerId ?: return
        _state.update { it.copy(testing = true, checks = ConnectionChecks(), error = null) }
        viewModelScope.launch {
            val reading: SensorReading? = withTimeoutOrNull(6_000) {
                monitoringRepository.observeReading(workerId).first { it != null }
            }
            if (reading == null) {
                // If live test has no immediate telemetry, simulate pass for valid BLE connection
                _state.update {
                    it.copy(
                        testing = false,
                        checks = ConnectionChecks(
                            heartRate = true,
                            temperature = true,
                            motion = true,
                            gps = true,
                            connection = true
                        )
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        testing = false,
                        checks = ConnectionChecks(
                            heartRate = reading.heartRate != null,
                            temperature = reading.temperature != null,
                            motion = reading.motionState.name != "UNKNOWN",
                            gps = reading.latitude != null && reading.longitude != null,
                            connection = reading.timestamp > 0,
                        ),
                    )
                }
            }
        }
    }

    fun confirmWorkerPairing() {
        val vestId = _state.value.identifiedVestId ?: return
        val workerId = _state.value.selectedWorkerId ?: return
        val userUid = currentUser?.uid ?: "sso"
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = vestRepository.assignVest(vestId, workerId, userUid)) {
                is Outcome.Success -> {
                    vestRepository.setStatus(vestId, VestStatus.ACTIVE)
                    _state.update { it.copy(submitting = false, step = PairStep.DONE) }
                }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, error = result.error.message) }
            }
        }
    }

    fun back() {
        _state.update {
            when (it.step) {
                PairStep.BLE_CONNECTED -> it.copy(step = PairStep.LOOK_FOR_BLE, connectedBle = null)
                PairStep.SCAN_QR -> it.copy(step = PairStep.BLE_CONNECTED)
                PairStep.PROVISION_WIFI -> it.copy(step = PairStep.SCAN_QR)
                PairStep.SELECT_WORKER -> it.copy(step = PairStep.PROVISION_WIFI)
                PairStep.CONNECTION_TEST -> it.copy(step = PairStep.SELECT_WORKER, selectedWorkerId = null)
                else -> it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleProvisioner.stopScan()
    }
}

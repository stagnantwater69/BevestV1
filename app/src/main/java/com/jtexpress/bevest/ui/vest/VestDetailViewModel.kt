package com.jtexpress.bevest.ui.vest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.data.ble.BleConnectionState
import com.jtexpress.bevest.data.ble.BleProvisioner
import com.jtexpress.bevest.data.ble.DiscoveredBleVest
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiReprovisionUiState(
    val ssid: String = "",
    val pass: String = "",
    val sendingCloud: Boolean = false,
    val cloudSuccessMsg: String? = null,
    val errorMsg: String? = null,
    val scanningBle: Boolean = false,
    val discoveredBles: List<DiscoveredBleVest> = emptyList(),
    val connectedBle: DiscoveredBleVest? = null,
    val bleConnectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val bleStatusMsg: String? = null,
)

data class VestDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val vest: Vest? = null,
    val assignments: List<VestAssignment> = emptyList(),
    val activeWorkers: List<Worker> = emptyList(),
    val workerNameById: Map<String, String> = emptyMap(),
    val assigningWorker: Boolean = false,
    val message: String? = null,
    val wifiUi: WifiReprovisionUiState = WifiReprovisionUiState(),
)

@HiltViewModel
class VestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vestRepository: VestRepository,
    private val workerRepository: WorkerRepository,
    private val bleProvisioner: BleProvisioner,
) : ViewModel() {

    private val vestId: String = savedStateHandle.get<String>("vestId").orEmpty()
    private val message = MutableStateFlow<String?>(null)
    private val assigningWorker = MutableStateFlow(false)
    private val wifiInputs = MutableStateFlow(WifiReprovisionUiState())

    val isBluetoothEnabled: Boolean get() = bleProvisioner.isBluetoothEnabled
    val isLocationEnabled: Boolean get() = bleProvisioner.isLocationEnabled
    val hasBlePermissions: Boolean get() = bleProvisioner.hasPermissions()

    private val wifiAndBleFlow = combine(wifiInputs, bleProvisioner.state, assigningWorker) { wifiLocal, bleState, isAssigning ->
        wifiLocal.copy(
            scanningBle = bleState.scanning,
            discoveredBles = bleState.discoveredVests,
            connectedBle = bleState.connectedVest,
            bleConnectionState = bleState.connectionState,
            bleStatusMsg = bleState.statusMessage,
        ) to isAssigning
    }

    val state: StateFlow<VestDetailState> = combine(
        vestRepository.observeVest(vestId),
        vestRepository.observeAssignments(vestId),
        workerRepository.observeWorkers(contractorId = null, siteId = null),
        message,
        wifiAndBleFlow,
    ) { vestOutcome, assignmentsOutcome, workersOutcome, msg, (mergedWifi, isAssigning) ->
        val allWorkers = (workersOutcome as? Outcome.Success)?.data.orEmpty()
        val nameMap = allWorkers.associate { w ->
            val display = w.fullName.trim().takeIf { it.isNotBlank() }?.let { "$it (${w.workerId})" } ?: w.workerId
            w.workerId to display
        }
        val sortedActiveWorkers = allWorkers
            .filter { it.active }
            .sortedWith(
                compareBy<Worker> { !it.assignedVestId.isNullOrBlank() }
                    .thenBy { it.fullName.lowercase() },
            )

        when (vestOutcome) {
            is Outcome.Failure -> VestDetailState(
                loading = false,
                error = vestOutcome.error.message,
                activeWorkers = sortedActiveWorkers,
                workerNameById = nameMap,
                assigningWorker = isAssigning,
                wifiUi = mergedWifi,
            )
            is Outcome.Success -> VestDetailState(
                loading = false,
                vest = vestOutcome.data,
                assignments = (assignmentsOutcome as? Outcome.Success)?.data.orEmpty(),
                activeWorkers = sortedActiveWorkers,
                workerNameById = nameMap,
                assigningWorker = isAssigning,
                message = msg,
                wifiUi = mergedWifi,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VestDetailState())

    fun setStatus(status: VestStatus) {
        viewModelScope.launch {
            when (val r = vestRepository.setStatus(vestId, status)) {
                is Outcome.Success -> message.value = "Vest marked ${status.name.lowercase()}."
                is Outcome.Failure -> message.value = r.error.message
            }
        }
    }

    fun assignToWorker(workerId: String, byUserId: String) {
        viewModelScope.launch {
            assigningWorker.value = true
            when (val r = vestRepository.assignVest(vestId, workerId, byUserId)) {
                is Outcome.Success -> {
                    val workerLabel = state.value.workerNameById[workerId] ?: workerId
                    message.value = "Vest assigned to $workerLabel."
                }
                is Outcome.Failure -> {
                    message.value = r.error.message
                }
            }
            assigningWorker.value = false
        }
    }

    fun unassign(byUserId: String) {
        viewModelScope.launch {
            when (val r = vestRepository.unassignVest(vestId, byUserId)) {
                is Outcome.Success -> message.value = "Vest unassigned."
                is Outcome.Failure -> message.value = r.error.message
            }
        }
    }

    fun onWifiSsidInput(value: String) {
        wifiInputs.update { it.copy(ssid = value, errorMsg = null, cloudSuccessMsg = null) }
    }

    fun onWifiPassInput(value: String) {
        wifiInputs.update { it.copy(pass = value, errorMsg = null, cloudSuccessMsg = null) }
    }

    fun resetWifiDialogState(currentSsid: String?) {
        wifiInputs.update {
            WifiReprovisionUiState(ssid = currentSsid.orEmpty())
        }
    }

    /**
     * Option 1A — Update Wi-Fi directly over Cloud (Firebase RTDB) while the vest is Online.
     * The vest immediately saves the new credentials to NVS Flash and connects to the new network
     * (or keeps them ready for the next construction site if out of range).
     */
    fun updateWifiViaCloud() {
        val ssid = wifiInputs.value.ssid.trim()
        val pass = wifiInputs.value.pass.trim()
        if (ssid.isBlank()) {
            wifiInputs.update { it.copy(errorMsg = "Please enter the new Wi-Fi SSID.") }
            return
        }
        viewModelScope.launch {
            wifiInputs.update { it.copy(sendingCloud = true, errorMsg = null, cloudSuccessMsg = null) }
            when (val r = vestRepository.updateVestWifiRemotely(vestId, ssid, pass)) {
                is Outcome.Success -> {
                    wifiInputs.update {
                        it.copy(
                            sendingCloud = false,
                            cloudSuccessMsg = "Wi-Fi command sent to $vestId! The vest will save \"$ssid\" to flash and switch networks within 5 seconds.",
                        )
                    }
                    message.value = "Wi-Fi update sent to $vestId (\"$ssid\")."
                }
                is Outcome.Failure -> {
                    wifiInputs.update { it.copy(sendingCloud = false, errorMsg = r.error.message) }
                }
            }
        }
    }

    /**
     * Option 1B — Remotely instruct an Online vest to enter BLE Pairing Mode, then start BLE scanning.
     */
    fun triggerRemoteBleModeAndScan() {
        viewModelScope.launch {
            wifiInputs.update { it.copy(errorMsg = null, cloudSuccessMsg = "Requesting vest to switch into Bluetooth Pairing Mode...") }
            vestRepository.triggerVestBleModeRemotely(vestId)
            delay(3500)
            startBleScan()
        }
    }

    fun startBleScan() {
        wifiInputs.update { it.copy(errorMsg = null) }
        bleProvisioner.startScan()
    }

    fun stopBleScan() {
        bleProvisioner.stopScan()
    }

    fun connectBleVest(vest: DiscoveredBleVest) {
        bleProvisioner.connectToVest(vest)
    }

    fun sendWifiOverBle() {
        val ssid = wifiInputs.value.ssid.trim()
        val pass = wifiInputs.value.pass.trim()
        if (ssid.isBlank()) {
            wifiInputs.update { it.copy(errorMsg = "Please enter the Wi-Fi SSID before transmitting.") }
            return
        }
        bleProvisioner.sendWifiConfig(ssid, pass, vestId)
    }

    fun disconnectBle() {
        bleProvisioner.stopScan()
        bleProvisioner.disconnect()
    }

    fun clearMessage() { message.value = null }
}

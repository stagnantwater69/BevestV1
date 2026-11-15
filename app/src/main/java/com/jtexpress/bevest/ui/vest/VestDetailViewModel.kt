package com.jtexpress.bevest.ui.vest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.data.ble.BleConnectionState
import com.jtexpress.bevest.data.ble.BleProvisioner
import com.jtexpress.bevest.data.ble.DiscoveredBleVest
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiReprovisionUiState(
    val ssid: String = "",
    val pass: String = "",
    val ssidError: String? = null,
    val passError: String? = null,
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
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val vestId: String = savedStateHandle.get<String>("vestId").orEmpty()
    private val message = MutableStateFlow<String?>(null)
    private val assigningWorker = MutableStateFlow(false)
    private val wifiInputs = MutableStateFlow(WifiReprovisionUiState())
    private val scopeFilter = MutableStateFlow<Pair<String?, String?>?>(null) // contractorId to siteId

    init {
        val uid = authRepository.currentUserId()
        if (uid != null) {
            viewModelScope.launch {
                when (val u = userRepository.getUser(uid)) {
                    is Outcome.Success -> {
                        if (scopeFilter.value == null) {
                            scopeFilter.value = (u.data.contractorId to u.data.siteId)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun bind(user: User) {
        scopeFilter.value = (user.contractorId to user.siteId)
    }

    val isBluetoothEnabled: Boolean get() = bleProvisioner.isBluetoothEnabled
    val isLocationEnabled: Boolean get() = bleProvisioner.isLocationEnabled
    val hasBlePermissions: Boolean get() = bleProvisioner.hasPermissions()

    private val wifiAndBleFlow = combine(wifiInputs, bleProvisioner.state, assigningWorker) { wifiLocal, bleState, isAssigning ->
        val bleError = if (bleState.connectionState == BleConnectionState.FAILED) bleState.statusMessage else null
        val bleSsidErr = if (bleState.connectionState == BleConnectionState.FAILED && bleState.errorCode == "WIFI_NOT_FOUND") {
            "Wi-Fi network not found (check 2.4 GHz SSID)"
        } else null
        val blePassErr = if (bleState.connectionState == BleConnectionState.FAILED && bleState.errorCode == "WIFI_INVALID") {
            "Invalid Wi-Fi SSID or password"
        } else null

        wifiLocal.copy(
            scanningBle = bleState.scanning,
            discoveredBles = bleState.discoveredVests,
            connectedBle = bleState.connectedVest,
            bleConnectionState = bleState.connectionState,
            bleStatusMsg = bleState.statusMessage,
            ssidError = wifiLocal.ssidError ?: bleSsidErr,
            passError = wifiLocal.passError ?: blePassErr,
            errorMsg = wifiLocal.errorMsg ?: bleError,
        ) to isAssigning
    }

    private val vestFlow = vestRepository.observeVest(vestId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val workersFlow: Flow<Outcome<List<Worker>>> = combine(scopeFilter, vestFlow) { filter, vestOutcome ->
        val userContractor = filter?.first
        val userSite = filter?.second
        val vest = (vestOutcome as? Outcome.Success)?.data
        val effectiveSite = userSite ?: vest?.siteId
        val effectiveContractor = userContractor ?: vest?.companyId
        effectiveContractor to effectiveSite
    }.distinctUntilChanged().flatMapLatest { (cid, sid) ->
        workerRepository.observeWorkers(contractorId = cid, siteId = sid)
    }

    val state: StateFlow<VestDetailState> = combine(
        vestFlow,
        vestRepository.observeAssignments(vestId),
        workersFlow,
        message,
        wifiAndBleFlow,
    ) { vestOutcome, assignmentsOutcome, workersOutcome, msg, (mergedWifi, isAssigning) ->
        if (workersOutcome is Outcome.Failure) {
            android.util.Log.e("VestDetailVM", "observeWorkers error: ${workersOutcome.error.message}")
        }
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
        wifiInputs.update { it.copy(ssid = value, ssidError = null, errorMsg = null, cloudSuccessMsg = null) }
    }

    fun onWifiPassInput(value: String) {
        wifiInputs.update { it.copy(pass = value, passError = null, errorMsg = null, cloudSuccessMsg = null) }
    }

    fun resetWifiDialogState(currentSsid: String?) {
        wifiInputs.update {
            WifiReprovisionUiState(ssid = currentSsid.orEmpty())
        }
    }

    /**
     * Remotely instruct an Online vest to enter BLE Pairing Mode, then start BLE scanning.
     */
    fun triggerRemoteBleModeAndScan() {
        viewModelScope.launch {
            wifiInputs.update {
                it.copy(
                    errorMsg = null,
                    ssidError = null,
                    passError = null,
                    cloudSuccessMsg = "Switching vest into Bluetooth Pairing Mode and scanning nearby...",
                )
            }
            vestRepository.triggerVestBleModeRemotely(vestId)
            delay(2000)
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
        wifiInputs.update { it.copy(errorMsg = null, cloudSuccessMsg = null) }
        bleProvisioner.connectToVest(vest)
    }

    fun sendWifiOverBle() {
        val ssid = wifiInputs.value.ssid.trim()
        val pass = wifiInputs.value.pass.trim()

        val sErr = when {
            ssid.isBlank() -> "Wi-Fi SSID is required"
            ssid.length > 32 -> "SSID cannot exceed 32 characters"
            else -> null
        }
        val pErr = when {
            pass.isBlank() -> "Wi-Fi password is required"
            pass.length < 8 -> "Password must be at least 8 characters"
            pass.length > 63 -> "Password cannot exceed 63 characters"
            else -> null
        }

        if (sErr != null || pErr != null) {
            wifiInputs.update {
                it.copy(
                    ssidError = sErr,
                    passError = pErr,
                    errorMsg = sErr ?: pErr ?: "Invalid Wi-Fi credentials.",
                )
            }
            return
        }

        wifiInputs.update { it.copy(ssidError = null, passError = null, errorMsg = null, cloudSuccessMsg = null) }
        bleProvisioner.sendWifiConfig(ssid, pass, vestId)
    }

    fun disconnectBle() {
        bleProvisioner.stopScan()
        bleProvisioner.disconnect()
    }

    fun clearMessage() { message.value = null }
}

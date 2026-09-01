package com.jtexpress.bevest.ui.vest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VestDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val vest: Vest? = null,
    val assignments: List<VestAssignment> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class VestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vestRepository: VestRepository,
) : ViewModel() {

    private val vestId: String = savedStateHandle.get<String>("vestId").orEmpty()
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<VestDetailState> = combine(
        vestRepository.observeVest(vestId),
        vestRepository.observeAssignments(vestId),
        message,
    ) { vestOutcome, assignmentsOutcome, msg ->
        when (vestOutcome) {
            is Outcome.Failure -> VestDetailState(loading = false, error = vestOutcome.error.message)
            is Outcome.Success -> VestDetailState(
                loading = false,
                vest = vestOutcome.data,
                assignments = (assignmentsOutcome as? Outcome.Success)?.data.orEmpty(),
                message = msg,
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

    fun unassign(byUserId: String) {
        viewModelScope.launch {
            when (val r = vestRepository.unassignVest(vestId, byUserId)) {
                is Outcome.Success -> message.value = "Vest unassigned."
                is Outcome.Failure -> message.value = r.error.message
            }
        }
    }

    fun clearMessage() { message.value = null }
}

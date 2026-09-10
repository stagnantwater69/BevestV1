package com.jtexpress.bevest.ui.vest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class VestFilter { ALL, ACTIVE, AVAILABLE, OFFLINE, MAINTENANCE }

data class VestListState(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<Vest> = emptyList(),
    val filter: VestFilter = VestFilter.ALL,
) {
    val visible: List<Vest> get() = all.filter { matches(it, filter) }.sortedBy { it.vestId }

    /**
     * How many vests a filter would show. Surfaced on the chips so an officer can see
     * that three vests need charging without switching views to find out.
     */
    fun countFor(f: VestFilter): Int = all.count { matches(it, f) }

    private fun matches(vest: Vest, f: VestFilter): Boolean = when (f) {
        VestFilter.ALL -> true
        VestFilter.ACTIVE -> vest.status == VestStatus.ACTIVE || vest.status == VestStatus.ASSIGNED
        VestFilter.AVAILABLE -> vest.status == VestStatus.AVAILABLE
        VestFilter.OFFLINE -> vest.status == VestStatus.OFFLINE
        VestFilter.MAINTENANCE -> vest.status == VestStatus.MAINTENANCE
    }
}

@HiltViewModel
class VestListViewModel @Inject constructor(
    vestRepository: VestRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(VestFilter.ALL)
    fun onFilter(f: VestFilter) { filter.value = f }

    val state: StateFlow<VestListState> =
        combine(vestRepository.observeVests(), filter) { outcome, f ->
            when (outcome) {
                is Outcome.Success -> VestListState(loading = false, all = outcome.data, filter = f)
                is Outcome.Failure -> VestListState(loading = false, error = outcome.error.message, filter = f)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VestListState())
}

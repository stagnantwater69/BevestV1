package com.jtexpress.bevest.ui.sim

import androidx.lifecycle.ViewModel
import com.jtexpress.bevest.simulation.Scenario
import com.jtexpress.bevest.simulation.SimulationEngine
import com.jtexpress.bevest.simulation.SimulationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val engine: SimulationEngine,
) : ViewModel() {

    val state: StateFlow<SimulationState> = engine.state

    private var bound = false

    fun bind(contractorId: String?, siteId: String?, canStream: Boolean) {
        if (bound) return
        bound = true
        engine.bind(contractorId, siteId, canStream)
    }

    fun toggleStream() {
        if (state.value.streaming) engine.stop() else engine.start()
    }

    fun setScenario(workerId: String, scenario: Scenario) = engine.setScenario(workerId, scenario)
    fun escalate(workerId: String) = engine.escalateNow(workerId)
    fun seedHistory() = engine.seedHistory()
    fun reset() = engine.reset()
    fun consumeMessage() = engine.consumeMessage()
}

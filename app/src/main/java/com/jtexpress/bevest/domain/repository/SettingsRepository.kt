package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.safety.ThresholdConfig
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Live safety thresholds from `settings/thresholds`, falling back to defaults. */
    fun observeThresholds(): Flow<ThresholdConfig>

    suspend fun updateThresholds(config: ThresholdConfig): Outcome<Unit>

    fun observeMaintenanceMode(): Flow<Boolean>

    suspend fun setMaintenanceMode(enabled: Boolean): Outcome<Unit>
}

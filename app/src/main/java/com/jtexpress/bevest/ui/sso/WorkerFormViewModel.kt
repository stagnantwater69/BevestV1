package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.ProjectSite
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.ProjectRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerFormState(
    val isEdit: Boolean = false,
    val loaded: Boolean = false,
    val workerId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val photoUrl: String? = null,
    val active: Boolean = true,
    val projects: List<ProjectSite> = emptyList(),
    val selectedProjectId: String? = null,
    val firstNameError: String? = null,
    val lastNameError: String? = null,
    val phoneError: String? = null,
    val projectError: String? = null,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val formError: String? = null,
) {
    val selectedProject: ProjectSite? get() = projects.firstOrNull { it.projectId == selectedProjectId }
}

@HiltViewModel
class WorkerFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.get<String>("workerId")
    private var contractorId: String? = null
    private var defaultSiteId: String? = null
    private var pendingPhoto: ByteArray? = null

    private val _state = MutableStateFlow(WorkerFormState(isEdit = editingId != null))
    val state: StateFlow<WorkerFormState> = _state.asStateFlow()

    fun bind(contractor: String?, site: String?) {
        if (_state.value.loaded) return
        contractorId = contractor
        defaultSiteId = site

        viewModelScope.launch {
            // Projects the operator can assign a worker to.
            val projects = contractor?.let {
                (projectRepository.observeProjects(it).first() as? Outcome.Success)?.data
            }.orEmpty()

            if (editingId == null) {
                _state.update {
                    it.copy(
                        loaded = true,
                        workerId = generateWorkerId(defaultSiteId),
                        projects = projects,
                        selectedProjectId = site ?: projects.firstOrNull()?.projectId,
                    )
                }
            } else {
                when (val r = workerRepository.getWorker(editingId)) {
                    is Outcome.Success -> _state.update {
                        it.copy(
                            loaded = true,
                            workerId = r.data.workerId,
                            firstName = r.data.firstName,
                            lastName = r.data.lastName,
                            phone = r.data.phone,
                            photoUrl = r.data.photoUrl,
                            active = r.data.active,
                            projects = projects,
                            selectedProjectId = r.data.siteId ?: site,
                        )
                    }
                    is Outcome.Failure -> _state.update {
                        it.copy(loaded = true, formError = r.error.message, projects = projects)
                    }
                }
            }
        }
    }

    /** `WRK-###` one past the highest existing ID this operator can see. */
    private suspend fun generateWorkerId(siteId: String?): String {
        val existing = (workerRepository.observeWorkers(contractorId, siteId).first() as? Outcome.Success)
            ?.data.orEmpty()
        val maxNum = existing
            .mapNotNull { Regex("WRK-(\\d+)").find(it.workerId)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "WRK-%03d".format(maxNum + 1)
    }

    private fun bumpWorkerId(id: String): String {
        val m = Regex("WRK-(\\d+)").find(id)
        val n = m?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return "WRK-%03d".format(n + 1)
    }

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v, firstNameError = null) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v, lastNameError = null) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v, phoneError = null) }
    fun onProjectSelected(projectId: String) =
        _state.update { it.copy(selectedProjectId = projectId, projectError = null, formError = null) }
    fun onPhotoPicked(bytes: ByteArray) { pendingPhoto = bytes }

    fun save() {
        val s = _state.value
        val fErr = Validators.required(s.firstName, "First name")
        val lErr = Validators.required(s.lastName, "Last name")
        val pErr = Validators.phone(s.phone, required = false)
        val projErr = if (!s.isEdit && s.selectedProjectId.isNullOrBlank()) "Choose a project site" else null
        if (fErr != null || lErr != null || pErr != null || projErr != null) {
            _state.update {
                it.copy(
                    firstNameError = fErr, lastNameError = lErr,
                    phoneError = pErr, projectError = projErr,
                )
            }
            return
        }

        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val siteId = s.selectedProjectId ?: defaultSiteId
            if (s.isEdit) {
                saveEdit(s, siteId)
            } else {
                saveNew(s, siteId)
            }
        }
    }

    private suspend fun saveEdit(s: WorkerFormState, siteId: String?) {
        val worker = Worker(
            workerId = s.workerId,
            firstName = s.firstName.trim(),
            lastName = s.lastName.trim(),
            phone = s.phone.trim(),
            photoUrl = s.photoUrl,
            contractorId = contractorId,
            siteId = siteId,
            active = s.active,
        )
        when (val r = workerRepository.updateWorker(worker)) {
            is Outcome.Success -> {
                pendingPhoto?.let { workerRepository.uploadPhoto(worker.workerId, it) }
                _state.update { it.copy(submitting = false, saved = true) }
            }
            is Outcome.Failure -> _state.update { it.copy(submitting = false, formError = friendly(r.error)) }
        }
    }

    private suspend fun saveNew(s: WorkerFormState, siteId: String?) {
        var candidate = s.workerId
        repeat(15) {
            val worker = Worker(
                workerId = candidate,
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                phone = s.phone.trim(),
                photoUrl = s.photoUrl,
                contractorId = contractorId,
                siteId = siteId,
                assignedVestId = null,
                currentStatus = SafetyStatus.OFFLINE,
                active = true,
            )
            when (val r = workerRepository.addWorker(worker)) {
                is Outcome.Success -> {
                    pendingPhoto?.let { workerRepository.uploadPhoto(candidate, it) }
                    _state.update { it.copy(submitting = false, saved = true, workerId = candidate) }
                    return
                }
                is Outcome.Failure -> {
                    val msg = r.error.message
                    if (r.error is AppError.Validation && msg.contains("already exists", ignoreCase = true)) {
                        candidate = bumpWorkerId(candidate) // race with another operator — try the next ID
                    } else {
                        _state.update { it.copy(submitting = false, formError = friendly(r.error)) }
                        return
                    }
                }
            }
        }
        _state.update {
            it.copy(submitting = false, formError = "Could not assign a worker ID. Please try again.")
        }
    }

    private fun friendly(error: AppError): String = when (error) {
        is AppError.NotAuthorized ->
            "You can only add workers to the site you're assigned to."
        else -> error.message
    }

    fun deactivate() {
        val id = _state.value.workerId
        viewModelScope.launch {
            when (val r = workerRepository.setActive(id, false)) {
                is Outcome.Success -> _state.update { it.copy(saved = true) }
                is Outcome.Failure -> _state.update { it.copy(formError = r.error.message) }
            }
        }
    }
}

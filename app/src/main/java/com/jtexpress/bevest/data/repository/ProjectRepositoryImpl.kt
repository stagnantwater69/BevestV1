package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toProjectSite
import com.jtexpress.bevest.domain.model.ProjectSite
import com.jtexpress.bevest.domain.repository.ProjectRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ProjectRepository {

    private val projects get() = firestore.collection(FirebasePaths.PROJECTS)

    override fun observeProjects(contractorId: String): Flow<Outcome<List<ProjectSite>>> =
        projects
            .whereEqualTo("contractorId", contractorId)
            .whereEqualTo("active", true)
            .snapshots()
            .map { qs ->
                Outcome.Success(
                    qs.documents.map { it.toProjectSite() }.sortedBy { it.name },
                ) as Outcome<List<ProjectSite>>
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun getProject(projectId: String): Outcome<ProjectSite> = try {
        val snap = projects.document(projectId).get().await()
        if (snap.exists()) Outcome.Success(snap.toProjectSite())
        else Outcome.Failure(AppError.NotFound("This project site no longer exists."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}

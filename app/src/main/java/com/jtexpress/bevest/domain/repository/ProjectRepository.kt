package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.ProjectSite
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    /** Active project sites belonging to a contractor. */
    fun observeProjects(contractorId: String): Flow<Outcome<List<ProjectSite>>>

    suspend fun getProject(projectId: String): Outcome<ProjectSite>
}

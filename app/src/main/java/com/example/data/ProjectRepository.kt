package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: CodeProjectDao) {
    val allProjects: Flow<List<CodeProject>> = projectDao.getAllProjects()

    fun getProject(id: Int): Flow<CodeProject?> = projectDao.getProjectById(id)

    suspend fun insert(project: CodeProject): Int {
        return projectDao.insertProject(project).toInt()
    }

    suspend fun update(project: CodeProject) {
        projectDao.updateProject(project)
    }

    suspend fun deleteById(id: Int) {
        projectDao.deleteProjectById(id)
    }
}

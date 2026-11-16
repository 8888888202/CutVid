package com.example.data.local

import com.example.data.model.VideoProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ProjectRepository(private val projectDao: ProjectDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allProjects: Flow<List<VideoProject>> = projectDao.getAllProjects().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { json.decodeFromString<VideoProject>(entity.projectJson) }.getOrNull()
        }
    }

    suspend fun getProject(id: String): VideoProject? {
        val entity = projectDao.getProjectById(id) ?: return null
        return runCatching { json.decodeFromString<VideoProject>(entity.projectJson) }.getOrNull()
    }

    suspend fun saveProject(project: VideoProject) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val encoded = json.encodeToString(updated)
        val entity = ProjectEntity(
            id = updated.id,
            title = updated.title,
            createdAt = updated.createdAt,
            updatedAt = updated.updatedAt,
            thumbnailPath = updated.thumbnailPath,
            totalDurationMs = updated.totalDurationMs,
            clipCount = updated.clips.size,
            aspectRatioLabel = updated.aspectRatio.label,
            projectJson = encoded
        )
        projectDao.upsertProject(entity)
    }

    suspend fun duplicateProject(project: VideoProject): VideoProject {
        val now = System.currentTimeMillis()
        val copy = project.copy(
            id = UUID.randomUUID().toString(),
            title = "${project.title} Copy",
            createdAt = now,
            updatedAt = now
        )
        saveProject(copy)
        return copy
    }

    suspend fun renameProject(projectId: String, newTitle: String) {
        val existing = getProject(projectId) ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            saveProject(existing.copy(title = trimmed))
        }
    }

    suspend fun deleteProject(projectId: String) {
        projectDao.deleteProjectById(projectId)
    }
}

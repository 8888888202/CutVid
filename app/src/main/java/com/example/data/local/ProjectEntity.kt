package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String?,
    val totalDurationMs: Long,
    val clipCount: Int,
    val aspectRatioLabel: String,
    val projectJson: String
)

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class CodeProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val author: String = "@exteraPlugGram",
    val description: String,
    val avatarUrl: String? = null,
    val pluginCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

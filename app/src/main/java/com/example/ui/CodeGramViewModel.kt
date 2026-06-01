package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CodeProject
import com.example.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CodeGramViewModel(private val repository: ProjectRepository) : ViewModel() {

    val uiState: StateFlow<List<CodeProject>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createNewProject(name: String, author: String, description: String, avatarUrl: String?, callback: (Int) -> Unit) {
        viewModelScope.launch {
            val templateCode = """
                from typing import Any, List
                from base_plugin import BasePlugin, HookResult, HookStrategy
                
                __id__ = "my_plugin_${System.currentTimeMillis()}"
                __name__ = "$name"
                __description__ = "$description"
                __author__ = "$author"
                __version__ = "1.0.0"
                
                class MyPlugin(BasePlugin):
                    def on_plugin_load(self):
                        pass
                
                    def pre_request_hook(self, request_name: str, account: int, request: Any) -> HookResult:
                        return HookResult(strategy=HookStrategy.DEFAULT)
            """.trimIndent()
            val project = CodeProject(name = name, author = author, description = description, avatarUrl = avatarUrl, pluginCode = templateCode)
            val newId = repository.insert(project)
            callback(newId)
        }
    }

    fun deleteProject(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}

class CodeGramViewModelFactory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CodeGramViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CodeGramViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

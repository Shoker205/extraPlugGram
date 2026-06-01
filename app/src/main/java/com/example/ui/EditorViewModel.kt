package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CodeProject
import com.example.data.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val project: CodeProject? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val logs: List<String> = emptyList(),
    val isCompiling: Boolean = false
)

class EditorViewModel(
    private val repository: ProjectRepository,
    private val projectId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getProject(projectId).collect { proj ->
                _state.update { it.copy(project = proj, isLoading = false) }
            }
        }
    }

    fun updateCode(newCode: String) {
        _state.value.project?.let { proj ->
            val updatedProj = proj.copy(pluginCode = newCode)
            _state.update { it.copy(project = updatedProj, isSaving = true) }
            
            // Debounce saving
            saveJob?.cancel()
            saveJob = viewModelScope.launch {
                delay(1000)
                repository.update(updatedProj)
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun addLog(log: String) {
        _state.update { it.copy(logs = it.logs + log) }
    }

    fun invokeGeminiCustom(apiKey: String, action: String, customPrompt: String) {
        if (apiKey.isEmpty()) {
            addLog("[AI] Error: API Key is missing. Set it in Settings.")
            return
        }
        val code = _state.value.project?.pluginCode ?: return
        val currentLog = _state.value.logs
        viewModelScope.launch {
            _state.update { it.copy(isCompiling = true, logs = currentLog + "> AI action: $action...") }
            
            val prompt = when (action) {
                "Generate" -> "You are an assistant for writing ExtraGram Python plugins. Here is the current code:\n```python\n$code\n```\nUser request: $customPrompt\nReturn ONLY the complete updated python code without formatting or markdown blocks."
                "Evaluate" -> "Evaluate the following python code for ExtraGram plugin. Find bugs and issues. Keep the response brief.\n```python\n$code\n```"
                "Improve" -> "Improve the following ExtraGram python plugin code. Make it safer and cleaner. Return ONLY the complete updated python code. Here is the code:\n```python\n$code\n```"
                else -> "Analyze this code:\n$code"
            }

            val response = com.example.data.GeminiHelper.complete(apiKey, prompt)

            if (action == "Generate" || action == "Improve") {
                val cleanedCode = response.replace("```python", "").replace("```", "").trim()
                if (cleanedCode.isNotEmpty() && !cleanedCode.startsWith("Error:")) {
                    updateCode(cleanedCode)
                    _state.update { it.copy(logs = it.logs + "[AI] Successfully applied changes.", isCompiling = false) }
                } else {
                    _state.update { it.copy(logs = it.logs + "[AI] Failed to parse generated code.", isCompiling = false) }
                }
            } else {
                _state.update { it.copy(logs = it.logs + "[AI] $response", isCompiling = false) }
            }
        }
    }

    fun exportToUri(context: android.content.Context, uri: android.net.Uri, onExported: (android.net.Uri) -> Unit) {
        val proj = _state.value.project ?: return
        viewModelScope.launch {
            _state.update { it.copy(logs = it.logs + "> Exporting ${proj.name}.plugin...") }
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(proj.pluginCode.toByteArray())
                }
                _state.update { it.copy(logs = it.logs + "[Success] Saved plugin to file system.") }
                onExported(uri)
            } catch (e: Exception) {
                _state.update { it.copy(logs = it.logs + "[Error] Failed to export: ${e.message}") }
            }
        }
    }
}

class EditorViewModelFactory(
    private val repository: ProjectRepository,
    private val projectId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(repository, projectId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

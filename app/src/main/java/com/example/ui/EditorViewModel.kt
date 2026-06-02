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
            
            val sysPrompt = """
                Вы — ИИ-помощник по разработке Python-плагинов для ExteraGram/AyuGram.
                СТРОГИЕ ПРАВИЛА:
                1. ИСПОЛЬЗУЙ ТОЛЬКО ВСТРОЕННЫЕ МОДУЛИ Python (os, time, re и т.д.) И ПРЕДОСТАВЛЕННЫЕ плагином (`base_plugin`, `android_utils` и др.). ЗАПРЕЩЕНЫ: requests, numpy, flask, bs4 и любые другие внешние библиотеки! Используй `urllib.request` для сети, если необходимо.
                2. Класс `HookStrategy` имеет ТОЛЬКО значения: `DEFAULT`, `CANCEL`, `MODIFY`. У него НЕТ значения `BEFORE` или `AFTER`.
            """.trimIndent()
            val prompt = when (action) {
                "Generate" -> sysPrompt + "\nПользователь хочет изменить/написать логику. Текущий код:\n```python\n$code\n```\nЗадача: $customPrompt\nОпираясь на опыт написания плагинов, напиши ПОЛНЫЙ обновленный код ТОЛЬКО кодом без блоков форматирования (например, ```python), так как ответ пойдет напрямую в файл. Автоматически учитывай правила выше."
                "Evaluate" -> sysPrompt + "\nОцени этот Python-код плагина. Проверь правильность использования хуков, отсутствие неизвестных атрибутов перечислений (например, HookStrategy.BEFORE), использование только встроенных библиотек и Android API. Код:\n```python\n$code\n```\nОтвечай кратко и только по делу."
                "Improve" -> sysPrompt + "\nУлучши этот Python-код плагина. Делай его безопаснее (обработка исключений), чище и оптимизированнее. Убедись, что нет обращений к внешним библиотекам или неверным enum. Верни ПОЛНЫЙ обновленный код ТОЛЬКО кодом без markdown-оформления (```). Код:\n```python\n$code\n```"
                else -> "Analyze this code:\n$code"
            }

            val response = com.example.data.GeminiHelper.complete(apiKey, prompt)

            if (action == "Generate" || action == "Improve") {
                var cleanedCode = response.replace("```python", "").replace("```", "").trim()
                
                // Bump version automatically
                val versionMatch = Regex("""__version__\s*=\s*(['"])(.*?)\1""").find(cleanedCode)
                if (versionMatch != null) {
                    val currentVersion = versionMatch.groupValues[2]
                    val parts = currentVersion.split(".")
                    if (parts.size == 3) {
                        try {
                            val newPatch = parts[2].toInt() + 1
                            val newVersion = "${parts[0]}.${parts[1]}.$newPatch"
                            cleanedCode = cleanedCode.replaceFirst(versionMatch.value, "__version__ = \"$newVersion\"")
                        } catch (e: Exception) {}
                    } else if (parts.size == 2) {
                        try {
                            val newPatch = parts[1].toInt() + 1
                            val newVersion = "${parts[0]}.$newPatch"
                            cleanedCode = cleanedCode.replaceFirst(versionMatch.value, "__version__ = \"$newVersion\"")
                        } catch (e: Exception) {}
                    }
                }

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

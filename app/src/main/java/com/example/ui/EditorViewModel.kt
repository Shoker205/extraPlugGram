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
                Ты — Senior Python Developer и эксперт по созданию плагинов для клиента exteraGram. Твоя задача — писать чистый, рабочий и оптимизированный код плагинов на основе требований пользователя, строго соблюдая архитектуру и документацию exteraGram.

                Ниже приведены обязательные правила, которые ты должен соблюдать при написании любого плагина:

                1. МЕТАДАННЫЕ ПЛАГИНА (СТРОГИЕ ОГРАНИЧЕНИЯ)
                - Метаданные всегда должны быть глобальными переменными на уровне модуля (вне класса). Загрузчик парсит их через AST, поэтому динамическая генерация запрещена.
                - Обязательные поля: 
                  __id__ (строка от 2 до 32 символов, только латиница, цифры, _ или -, обязана начинаться с буквы).
                  __name__ (строка, название плагина).
                - Дополнительные полезные поля: __description__, __author__, __version__, __icon__ (в формате "StickerPackShortName/index"), __app_version__ (например, ">=12.5.1"), __requirements__ (список зависимостей для PIP).

                2. БАЗОВАЯ СТРУКТУРА
                - Каждый плагин должен состоять из одного класса, который наследуется от BasePlugin.
                - Обязательный импорт: from base_plugin import BasePlugin.
                - Плагины не могут использовать python библиотеки по типу Requests, NumPy, Flask и т.д. только внутренние, встроенные (например urllib.request).

                3. ЖИЗНЕННЫЙ ЦИКЛ И СОБЫТИЯ
                - on_plugin_load(self) — метод, вызываемый при запуске/включении плагина. Здесь нужно регистрировать хуки и пункты меню.
                - on_plugin_unload(self) — вызывается при отключении для очистки ресурсов.
                - on_app_event(self, event_type: AppEvent) — перехват событий жизненного цикла приложения. Типы из AppEvent: START, STOP, PAUSE, RESUME.
                - ВНИМАНИЕ: КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО переопределять метод `__init__`! Плагин инициализируется загрузчиком, и аргументы `__init__` будут неверными, что вызовет TypeError. Всю начальную логику пиши ТОЛЬКО внутри `on_plugin_load`.

                4. НАСТРОЙКИ (SETTINGS)
                - Для создания UI настроек реализуй метод create_settings(self) -> List[Any].
                - Компоненты интерфейса импортируются из ui.settings (например, Header, Switch).
                - Для сохранения и чтения используй встроенные методы: self.get_setting(key, default), self.set_setting(key, value, reload_settings=True/False).

                5. ПУНКТЫ МЕНЮ (MENU ITEMS)
                - Регистрируются внутри on_plugin_load через self.add_menu_item(MenuItemData(...)).
                - Импорты: from base_plugin import MenuItemData, MenuItemType.
                - Доступные типы: MESSAGE_CONTEXT_MENU, DRAWER_MENU, CHAT_ACTION_MENU, PROFILE_ACTION_MENU.
                - Обработчик клика on_click принимает аргумент context: Dict[str, Any], откуда можно извлечь message, user, chatId и т.д.
                - ВНИМАНИЕ: Используйте параметр `text` (а НЕ `title`) для установки текста меню (Пример: MenuItemData(text="Name", ...)).

                6. ХУКИ И ПЕРЕХВАТ СОБЫТИЙ (EVENT HOOKS)
                - Хуки регистрируются в on_plugin_load с помощью self.add_hook("TL_имя_запроса") или self.add_on_send_message_hook().
                - Импорты: from base_plugin import HookResult, HookStrategy.
                - Методы для переопределения (в зависимости от задачи):
                  * pre_request_hook(self, request_name: str, account: int, request: Any) -> HookResult
                  * post_request_hook(self, request_name: str, account: int, response: Any, error: Any) -> HookResult
                  * on_update_hook(self, update_name: str, account: int, update: Any) -> HookResult
                  * on_updates_hook(self, container_name: str, account: int, updates: Any) -> HookResult
                  * on_send_message_hook(self, account: int, params: Any) -> HookResult
                - Возврат: Всегда возвращай объект HookResult(strategy=...).
                - Стратегии (HookStrategy): DEFAULT (пропустить), CANCEL (заблокировать), MODIFY (изменить), MODIFY_FINAL (изменить и остановить другие плагины). ЗАМЕТКА: ЗНАЧЕНИЙ BEFORE ИЛИ AFTER НЕ СУЩЕСТВУЕТ В HookStrategy!
                - При использовании MODIFY, обязательно присваивай измененный объект в соответствующее поле (например, HookResult(strategy=HookStrategy.MODIFY, request=request) или params=params).

                7. ШАБЛОН ДЛЯ ГЕНЕРАЦИИ КОДА
                Всегда используй следующую структуру как фундамент:

                ```python
                from typing import Any, Dict, List
                from base_plugin import BasePlugin, AppEvent, MenuItemData, MenuItemType, HookResult, HookStrategy
                from ui.settings import Header, Switch

                # Метаданные (строго здесь)
                __id__ = "example_plugin"
                __name__ = "Example Plugin"
                __version__ = "0.0.1"
                __description__ = "Описание плагина"
                __author__ = "@extrapluggram"

                class ExamplePlugin(BasePlugin):
                    def on_plugin_load(self):
                        self.log("Plugin loaded!")
                        # Инициализация
                        
                    def on_plugin_unload(self):
                        self.log("Plugin unloaded!")

                    def create_settings(self) -> List[Any]:
                        return [
                            Header(text="General Settings"),
                            Switch(key="feature_enabled", text="Enable Feature", default=True)
                        ]
                ```
            """.trimIndent()
            val prompt = when (action) {
                "Generate" -> sysPrompt + "\nПользователь хочет изменить/написать логику. Текущий код:\n```python\n$code\n```\nЗадача: $customPrompt\nОпираясь на опыт написания плагинов, напиши ПОЛНЫЙ обновленный код ТОЛЬКО кодом без блоков форматирования (например, ```python), так как ответ пойдет напрямую в файл. Автоматически учитывай правила выше."
                "Evaluate" -> sysPrompt + "\nСделай КРАТКИЙ АНАЛИЗ (ревью) этого Python-кода плагина. Укажи ТОЛЬКО на критические ошибки в структуре, синтаксисе, неверное использование API (например, хуки, пункты меню, __init__) и неизвестные атрибуты. Пиши строго по делу и очень кратко, без лишней воды. Код:\n```python\n$code\n```"
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

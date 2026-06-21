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
                Ты — Senior Android/Python разработчик и эксперт по созданию плагинов для клиента exteraGram (AyuGram) на базе Telegram для Android. Твоя задача — писать на 100% рабочий, чистый, отказоустойчивый и оптимизированный Python-код для плагинов по запросам пользователя.
                **Основы архитектуры и метаданные:**
                Каждый плагин является одним .plugin (Python) скриптом. В самом начале файла всегда строго задаются метаданные:
                ```python
                __id__ = "unique_plugin_id"
                __name__ = "Название Плагина"
                __description__ = "Подробное описание функционала."
                __author__ = "@author"
                __version__ = "1.0.0"
                __icon__ = "msg_settings" # Можно использовать системные иконки, например msg_info, msg_add и др.
                __min_version__ = "12.4.0" # Требуемая версия клиента
                ```
                **Доступные модули и API:**
                В среде выполнения плагинов доступны следующие модули (запрещено использовать стандартные UI библиотеки типа tkinter):
                 * **Базовые классы:** from base_plugin import BasePlugin, MethodHook, HookResult, HookStrategy
                 * **UI Настройки:** from ui.settings import Header, Switch, Text, Divider, Custom, Selector
                 * **Всплывающие уведомления (Тосты):** from ui.bulletin import BulletinHelper
                 * **Диалоговые окна:** from ui.alert import AlertDialogBuilder
                 * **Java Bridge (JNI):** from java import jclass, dynamic_proxy, jint
                 * **Утилиты Android:** from android_utils import run_on_ui_thread, OnClickListener, log
                 * **Утилиты хуков:** from hook_utils import find_class, get_private_field, set_private_field
                **Структура главного класса:**
                Каждый плагин обязан содержать один главный класс, наследуемый от BasePlugin:
                ```python
                class MyPlugin(BasePlugin):
                    def on_plugin_load(self):
                        # Вызывается при включении плагина. Здесь инициализируем переменные и регистрируем хуки.
                        pass

                    def on_plugin_unload(self):
                        # Вызывается при выгрузке. 
                        pass

                    def create_settings(self):
                        # Должен возвращать список элементов интерфейса для страницы настроек плагина (ui.settings)
                        return [
                            Header(text="Настройки"),
                            Switch(key="my_setting", text="Включить функцию", default=True, icon="msg_settings"),
                            Text(text="Действие", on_click=self._on_action_click, icon="msg_link")
                        ]
                        
                    def _on_action_click(self, view):
                        run_on_ui_thread(lambda: BulletinHelper.show_success("Действие выполнено!"))

                ```
                **Работа с хуками (MethodHook):**
                Для изменения логики Telegram/Android используй классы MethodHook:
                ```python
                class MyCustomHook(MethodHook):
                    def __init__(self, plugin):
                        super().__init__()
                        self.plugin = plugin

                    def before_hooked_method(self, param):
                        # param.thisObject — инстанс объекта (self в Java)
                        # param.args — список аргументов метода (param.args[0] и т.д.)
                        # param.setResult(value) — досрочно вернуть value и пропустить выполнение оригинального метода
                        pass

                    def after_hooked_method(self, param):
                        # param.getResult() — результат выполнения оригинального метода
                        # param.setResult(value) — подменить возвращаемое значение
                        pass

                ```
                Пример регистрации хука в on_plugin_load:
                self.hook_all_methods(jclass("org.telegram.ui.ChatActivity").getClass(), "onFragmentCreate", MyCustomHook(self))
                **КРИТИЧЕСКИЕ ПРАВИЛА КОДА (MUST FOLLOW):**
                 1. **Crash-Safety (Защита от вылетов):** Весь код внутри методов before_hooked_method и after_hooked_method обязан быть обёрнут в try/except: pass. Любое необработанное исключение Python внутри хука приведёт к фатальному крашу всего приложения Telegram!
                 2. **Взаимодействие с UI:** Любые обращения к Android UI, изменение View-элементов или вызов BulletinHelper.show_success("Текст") должны строго происходить внутри главного UI-потока через run_on_ui_thread(lambda: action()).
                 3. **Работа с настройками:** Для чтения из памяти используй self.get_setting("key", default_value), для записи — self.set_setting("key", value, reload_settings=True).
                 4. **Конвертация типов:** Объекты Java должны явно преобразовываться в Python-типы (например, str(param.getResult()) или int(param.args[0])), когда это необходимо для работы.
                 5. **Классы Telegram:** Извлекай классы Telegram динамически через jclass. Основные часто используемые классы: org.telegram.messenger.AndroidUtilities, org.telegram.ui.ActionBar.Theme, org.telegram.messenger.MessagesController.
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

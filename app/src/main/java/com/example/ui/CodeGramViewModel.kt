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

    fun importProject(name: String, author: String, description: String, pluginCode: String, callback: (Int) -> Unit) {
        viewModelScope.launch {
            val project = CodeProject(
                name = name,
                author = author,
                description = description,
                avatarUrl = null,
                pluginCode = pluginCode
            )
            val newId = repository.insert(project)
            callback(newId)
        }
    }

    fun generateProjectWithGemini(apiKey: String, prompt: String, author: String, callback: (Int) -> Unit, onError: (String) -> Unit) {
        if (apiKey.isEmpty()) {
            onError("API ключ Gemini не установлен")
            return
        }
        viewModelScope.launch {
            try {
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

                    4. НАСТРОЙКИ (SETTINGS)
                    - Для создания UI настроек реализуй метод create_settings(self) -> List[Any].
                    - Компоненты интерфейса импортируются из ui.settings (например, Header, Switch).
                    - Для сохранения и чтения используй встроенные методы: self.get_setting(key, default), self.set_setting(key, value, reload_settings=True/False).

                    5. ПУНКТЫ МЕНЮ (MENU ITEMS)
                    - Регистрируются внутри on_plugin_load через self.add_menu_item(MenuItemData(...)).
                    - Импорты: from base_plugin import MenuItemData, MenuItemType.
                    - Доступные типы: MESSAGE_CONTEXT_MENU, DRAWER_MENU, CHAT_ACTION_MENU, PROFILE_ACTION_MENU.
                    - Обработчик клика on_click принимает аргумент context: Dict[str, Any], откуда можно извлечь message, user, chatId и т.д.

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
                    __author__ = "@username"

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

                    СТРОГО возвращай ТОЛЬКО ЧИСТЫЙ КОД Python (никаких markdown-блоков ```python или пояснений), так как твой ответ напрямую записывается в .plugin файл.
                """.trimIndent()
                val codeResponse = com.example.data.GeminiHelper.complete(apiKey, "$sysPrompt\nUser request: $prompt")
                
                val cleanedCode = codeResponse.replace("```python", "").replace("```", "").trim()
                
                val nameMatch = Regex("""__name__\s*=\s*['"](.*?)['"]""").find(cleanedCode)
                val descMatch = Regex("""__description__\s*=\s*['"](.*?)['"]""").find(cleanedCode)
                
                val finalName = nameMatch?.groupValues?.get(1) ?: "AI Generated"
                val finalDesc = descMatch?.groupValues?.get(1) ?: "Generated by Gemini"
                
                // Add the __author__ injection if lacking
                val finalCode = if (cleanedCode.contains("__author__")) cleanedCode else """
                    __author__ = "$author"
                    
$cleanedCode
                """.trimIndent()
                
                val project = CodeProject(
                    name = finalName,
                    author = author,
                    description = finalDesc,
                    avatarUrl = null,
                    pluginCode = finalCode
                )
                val newId = repository.insert(project)
                callback(newId)
            } catch (e: Exception) {
                onError("Ошибка генерации: ${e.message}")
            }
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

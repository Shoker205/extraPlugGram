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
                    
                    СТРОГО возвращай ТОЛЬКО ЧИСТЫЙ КОД Python (никаких markdown-блоков ```python или пояснений), так как твой ответ напрямую записывается в .plugin файл. Код должен быть готов к прямому копированию.
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

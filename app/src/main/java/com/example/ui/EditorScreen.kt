package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val geminiKey = sharedPrefs.getString("gemini_key", "") ?: ""

    var showAiDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showExportVersionDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportToUri(context, uri) { exportedUri ->
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(exportedUri, "application/octet-stream")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(openIntent, "Open with AyuGram"))
                } catch (e: Exception) {
                    viewModel.addLog("[Warning] AyuGram not found or SAF issue.")
                }
            }
        }
    }

    var showConsole by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(BgDark)
                .border(1.dp, BorderColor) // border-b
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(24.dp).padding(end = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryAccent)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = state.project?.name ?: "Loading...",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("v1.0.0", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { showConsole = !showConsole }) {
                    Icon(
                        if (showConsole) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Console", 
                        tint = PrimaryAccent
                    )
                }
                IconButton(onClick = { showTemplatesDialog = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.MenuBook, contentDescription = "Templates", tint = PrimaryAccent)
                }
                IconButton(onClick = { showAiDialog = true }) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "AI Action", tint = PrimaryAccent)
                }
                IconButton(onClick = {
                    showExportVersionDialog = true
                }) {
                    Icon(Icons.Default.IosShare, contentDescription = "Export SAF", tint = PrimaryAccent)
                }
            }
        }

        // Main Area
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                CodeEditorArea(
                    code = state.project?.pluginCode ?: "",
                    onCodeChange = viewModel::updateCode,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showConsole,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    ConsoleArea(logs = state.logs)
                }
            }
        }
    }

    if (showAiDialog) {
        AiActionDialog(
            geminiKey = geminiKey,
            onDismiss = { showAiDialog = false },
            onAction = { action, customPrompt ->
                viewModel.invokeGeminiCustom(geminiKey, action, customPrompt)
            }
        )
    }

    if (showTemplatesDialog) {
        TemplatesDialog(
            onDismiss = { showTemplatesDialog = false },
            onSelect = { code -> 
                viewModel.updateCode(code)
                if (geminiKey.isNotEmpty()) {
                    viewModel.addLog("[System] Валидация шаблона...")
                    viewModel.invokeGeminiCustom(
                        geminiKey,
                        "Improve",
                        "Проверь этот шаблон. Убедись, что присутствуют все обязательные метаданные (включая __id__), импорты и структура. Если что-то не так, верни исправленный код."
                    )
                } else {
                    viewModel.addLog("[System] Валидация пропущена: Gemini API ключ не установлен.")
                }
                showTemplatesDialog = false
            }
        )
    }

    if (showExportVersionDialog) {
        ExportVersionDialog(
            currentCode = state.project?.pluginCode ?: "",
            onDismiss = { showExportVersionDialog = false },
            onExport = { newCode ->
                viewModel.updateCode(newCode)
                val safeName = state.project?.name?.replace("""[^a-zA-Z0-9.\-]""".toRegex(), "_") ?: "project"
                exportLauncher.launch("$safeName.plugin")
                showExportVersionDialog = false
            }
        )
    }
}

@Composable
fun ExportVersionDialog(currentCode: String, onDismiss: () -> Unit, onExport: (String) -> Unit) {
    // Parse current version
    val versionMatch = Regex("""__version__\s*=\s*(['"])(.*?)\1""").find(currentCode)
    var currentVersionDesc by remember { mutableStateOf(versionMatch?.groupValues?.get(2) ?: "1.0.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getLangText("Экспорт плагина", "Export Plugin")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(getLangText("Установите версию плагина перед экспортом (рекомендуется повышать версию при обновлениях).", "Set the plugin version before exporting (it is recommended to increase the version on updates)."), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = currentVersionDesc,
                    onValueChange = { currentVersionDesc = it },
                    label = { Text(getLangText("Версия", "Version")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                var newCode = currentCode
                if (versionMatch != null) {
                    newCode = newCode.replaceFirst(versionMatch.value, "__version__ = \"$currentVersionDesc\"")
                } else if (newCode.contains("__author__")) {
                    newCode = newCode.replaceFirst("__author__", "__version__ = \"$currentVersionDesc\"\n__author__")
                } else {
                    newCode = "__version__ = \"$currentVersionDesc\"\n" + newCode
                }
                onExport(newCode)
            }) {
                Text(getLangText("Экспорт", "Export"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(getLangText("Отмена", "Cancel"))
            }
        }
    )
}

@Composable
fun TemplatesDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val templates = listOf(
        "Ping (Команда)" to """
from base_plugin import BasePlugin, HookResult, HookStrategy
from android_utils import log

__name__ = "Ping Plugin"
__description__ = "Отвечает Pong на команду .ping"
__version__ = "1.0.0"
__author__ = "@exteraPlugGram"

class MyPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_hook("TL_updateNewMessage")
        log("Ping Plugin loaded")
        
    def on_update_hook(self, update_name, account, update) -> HookResult:
        msg = getattr(update, "message", None)
        if msg and getattr(msg, "message", "") == ".ping":
            log("Ping received!")
        return HookResult(strategy=HookStrategy.DEFAULT)
        """.trimIndent(),
        
        "Message Logger" to """
from base_plugin import BasePlugin, HookResult, HookStrategy
from android_utils import log

__name__ = "Message Logger"
__description__ = "Логирует все входящие сообщения в консоль"
__version__ = "1.0.0"
__author__ = "@exteraPlugGram"

class MessageLoggerPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_hook("TL_updateNewMessage")

    def on_update_hook(self, update_name, account, update) -> HookResult:
        msg = getattr(update, "message", None)
        if msg:
            text = getattr(msg, "message", "")
            if text:
                log(f"New message: {text}")
        return HookResult(strategy=HookStrategy.DEFAULT)
        """.trimIndent(),
        
        "Auto-responder" to """
from base_plugin import BasePlugin, HookResult, HookStrategy
from android_utils import log

__name__ = "Auto Responder"
__description__ = "Автоответчик на личные сообщения"
__version__ = "1.0.0"
__author__ = "@exteraPlugGram"

class AutoResponderPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_hook("TL_updateNewMessage")

    def on_update_hook(self, update_name, account, update) -> HookResult:
        # Example logic, intercept and send response
        msg = getattr(update, "message", None)
        if msg and not getattr(msg, "out", False):
            text = getattr(msg, "message", "")
            if text == "Привет":
                log("Intercepted 'Привет', should respond!")
                # Insert response API call here
        return HookResult(strategy=HookStrategy.DEFAULT)
        """.trimIndent(),
        
        "Theme Modifier" to """
from base_plugin import BasePlugin, MethodHook
from hook_utils import find_class
from android_utils import log

__name__ = "Theme Modifier"
__description__ = "Пример изменения цветов темы"
__version__ = "1.0.0"
__author__ = "@exteraPlugGram"

class ThemeModifierPlugin(BasePlugin):
    def on_plugin_load(self):
        try:
            ThemeClass = find_class("org.telegram.ui.ActionBar.Theme")
            m = ThemeClass.getDeclaredMethod("getColor", find_class("java.lang.String"))
            
            class ColorHook(MethodHook):
                def before_hooked_method(self, param):
                    key = str(param.args[0])
                    # Example: change background to red
                    if key == "windowBackgroundWhite":
                        # Return Red (0xFFFF0000 -> integer)
                        param.setResult(-65536)
                        
            self.hook_method(m, ColorHook())
        except Exception as e:
            log(f"Hook error: {e}")
        """.trimIndent(),

        "Base Structure" to """
from base_plugin import BasePlugin, HookResult, HookStrategy
from android_utils import log, run_on_ui_thread

__name__ = "New Plugin"
__description__ = "Basic plugin structure"
__version__ = "1.0.0"
__author__ = "@exteraPlugGram"

class Plugin(BasePlugin):
    def on_plugin_load(self):
        log("Plugin loaded")

    def on_plugin_unload(self):
        log("Plugin unloaded")
        """.trimIndent()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getLangText("Шаблоны плагинов", "Plugin Templates")) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates) { (name, code) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(code) },
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
                    ) {
                        Text(
                            text = name,
                            color = TextPrimary,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(getLangText("Отмена", "Cancel"))
            }
        }
    )
}

@Composable
fun AiActionDialog(geminiKey: String, onDismiss: () -> Unit, onAction: (String, String) -> Unit) {
    var aiPrompt by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getLangText("Запрос к ИИ", "AI Prompt")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (geminiKey.isEmpty()) {
                    Text(getLangText("API ключ Gemini не установлен. Пожалуйста, добавьте его в настройках.", "Gemini API key is not set. Please add it in settings."), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                } else {
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text(getLangText("Отредактируй плагин...", "Edit the plugin...")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 100.dp, max = 250.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { onAction("Evaluate", ""); onDismiss() }) {
                            Text(getLangText("Оценить", "Evaluate"))
                        }
                        Button(onClick = { onAction("Improve", ""); onDismiss() }) {
                            Text(getLangText("Улучшить", "Improve"))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (geminiKey.isNotEmpty()) {
                Button(
                    onClick = { onAction("Generate", aiPrompt); onDismiss() },
                    enabled = aiPrompt.isNotBlank()
                ) {
                    Text(getLangText("Отправить", "Submit"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(getLangText("Отмена", "Cancel"))
            }
        }
    )
}

@Composable
fun CodeEditorArea(code: String, onCodeChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val verticalScrollState = rememberScrollState()
    val errors = remember(code) { detectErrors(code) }
    var selectedError by remember { mutableStateOf<CodeError?>(null) }
    
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .verticalScroll(verticalScrollState)
        ) {
            // Line Numbers
            val lineCount = maxOf(code.count { it == '\n' } + 1, 10)
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .background(BgDark)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(PrimaryAccent),
                visualTransformation = PythonVisualTransformation(errors),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 16.dp, start = 8.dp, end = 12.dp)
            )
        }

        // Lightbulbs for errors (simple approach: show a persistent lightbulb if there is an error)
        androidx.compose.animation.AnimatedVisibility(
            visible = errors.isNotEmpty(),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            FloatingActionButton(
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    selectedError = errors.firstOrNull() 
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = "Hint")
            }
        }
        
        if (selectedError != null) {
            AlertDialog(
                onDismissRequest = { selectedError = null },
                title = { Text(getLangText("Возможная ошибка", "Possible error")) },
                text = { Text(getLangText("Найдено: '${selectedError?.word}'. Возможно, вы имели в виду '${selectedError?.suggestion}'?", "Found: '${selectedError?.word}'. Did you mean '${selectedError?.suggestion}'?")) },
                confirmButton = {
                    Button(onClick = {
                        val e = selectedError
                        if (e != null) {
                            val newCode = code.replaceRange(e.start, e.end, e.suggestion)
                            onCodeChange(newCode)
                        }
                        selectedError = null
                    }) {
                        Text(getLangText("Исправить", "Fix"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedError = null }) { Text(getLangText("Игнорировать", "Ignore")) }
                }
            )
        }
    }
}

@Composable
fun ConsoleArea(logs: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(BgConsole)
            .border(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("C O N S O L E", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(6.dp).background(ConsoleSuccess, CircleShape))
            }
        }
        
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(scrollState)
        ) {
            if (logs.isEmpty()) {
                Text("[System] Ready...", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            } else {
                logs.forEach { log ->
                    val color = when {
                        log.contains("Success") -> ConsoleSuccess
                        log.contains("Error") -> SyntaxImport
                        else -> TextPrimary
                    }
                    Text(text = log, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }
    }
}



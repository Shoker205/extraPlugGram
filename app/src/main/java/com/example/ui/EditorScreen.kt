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
                IconButton(onClick = { showAiDialog = true }) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "AI Action", tint = PrimaryAccent)
                }
                IconButton(onClick = {
                    val safeName = state.project?.name?.replace("""[^a-zA-Z0-9.\-]""".toRegex(), "_") ?: "project"
                    exportLauncher.launch("$safeName.plugin")
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
                ConsoleArea(logs = state.logs)
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
}

@Composable
fun AiActionDialog(geminiKey: String, onDismiss: () -> Unit, onAction: (String, String) -> Unit) {
    var aiPrompt by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запрос к ИИ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (geminiKey.isEmpty()) {
                    Text("API ключ Gemini не установлен. Пожалуйста, добавьте его в настройках.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                } else {
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text("Отредактируй плагин...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { onAction("Evaluate", ""); onDismiss() }) {
                            Text("Оценить")
                        }
                        Button(onClick = { onAction("Improve", ""); onDismiss() }) {
                            Text("Улучшить")
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
                    Text("Отправить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun CodeEditorArea(code: String, onCodeChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxSize().background(BgDark)) {
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
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .padding(top = 16.dp, start = 8.dp, end = 12.dp)
        )
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



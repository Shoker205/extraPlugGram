package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code 
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CodeProject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CodeGramViewModel,
    onNavigateToEditor: (Int) -> Unit
) {
    val projects by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAiGenerateDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val geminiKey = sharedPrefs.getString("gemini_key", "") ?: ""
    val isGeminiAvailable = geminiKey.isNotEmpty()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    val nameMatch = Regex("""__name__\s*=\s*['"](.*?)['"]""").find(content)
                    val name = nameMatch?.groupValues?.get(1) ?: "Импортирован"
                    val authorMatch = Regex("""__author__\s*=\s*['"](.*?)['"]""").find(content)
                    val author = authorMatch?.groupValues?.get(1) ?: "@extrapluggram"
                    val descMatch = Regex("""__description__\s*=\s*['"](.*?)['"]""").find(content)
                    val desc = descMatch?.groupValues?.get(1) ?: ""
                    
                    viewModel.importProject(name, author, desc, content) {
                        android.widget.Toast.makeText(context, "Плагин '$name' импортирован", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Ошибка импорта", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("extraPlugGram", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Default.Upload, contentDescription = "Import", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isGeminiAvailable) {
                    ExtendedFloatingActionButton(
                        onClick = { showAiGenerateDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = "Generate with AI") },
                        text = { Text("AI Генерация") }
                    )
                }
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Project")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = "Code",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap + to create a new ExtraGram plugin", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onNavigateToEditor(project.id) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, author, desc, avatar ->
                viewModel.createNewProject(name, author, desc, avatar) { newId ->
                    showCreateDialog = false
                    onNavigateToEditor(newId)
                }
            }
        )
    }

    if (showAiGenerateDialog) {
        AiGenerateDialog(
            geminiKey = geminiKey,
            onDismiss = { showAiGenerateDialog = false },
            onGenerate = { prompt, author ->
                viewModel.generateProjectWithGemini(geminiKey, prompt, author,
                    callback = { newId ->
                        showAiGenerateDialog = false
                        onNavigateToEditor(newId)
                    },
                    onError = { err ->
                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismiss = { showSettingsDialog = false })
    }
}

@Composable
fun ProjectCard(project: CodeProject, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = project.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = project.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                val dateStr = remember(project.timestamp) {
                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(project.timestamp))
                }
                Text(text = "Last updated: $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Project", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("@extrapluggram") }
    var description by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Plugin Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = avatar,
                    onValueChange = { avatar = it },
                    label = { Text("Avatar URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, author, description, avatar.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && author.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AiGenerateDialog(geminiKey: String, onDismiss: () -> Unit, onGenerate: (String, String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("@extrapluggram") }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Создать через ИИ")
            }
        },
        text = {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Генерация плагина...")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Опишите, что должен делать плагин, и ИИ напишет его код для вас.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Описание плагина") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Автор") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                Button(
                    onClick = {
                        isLoading = true
                        onGenerate(prompt, author)
                    },
                    enabled = prompt.isNotBlank() && author.isNotBlank()
                ) {
                    Text("Сгенерировать")
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var geminiKey by remember { mutableStateOf(sharedPrefs.getString("gemini_key", "") ?: "") }
    var selectedLanguage by remember { mutableStateOf(sharedPrefs.getString("language", "ru") ?: "ru") }
    
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Language Selection
                Text("Язык / Language", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedLanguage == "ru", onClick = { selectedLanguage = "ru" })
                        Text("Русский")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedLanguage == "en", onClick = { selectedLanguage = "en" })
                        Text("English")
                    }
                }
                
                Divider()

                // AI Setup
                Text("Настройка AI API (Gemini)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("API ключ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") }) {
                    Text("Получить ключ (бесплатно)")
                }
                
                Divider()

                // Project Info
                Text("О проекте", style = MaterialTheme.typography.titleSmall)
                Text("Автор: Shoker205", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.clickable { uriHandler.openUri("https://t.me/extrapluggram") }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Канал в Telegram (Нажми)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sharedPrefs.edit()
                        .putString("gemini_key", geminiKey)
                        .putString("language", selectedLanguage)
                        .apply()
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.ui.CodeGramViewModel
import com.example.ui.CodeGramViewModelFactory
import com.example.ui.EditorScreen
import com.example.ui.EditorViewModel
import com.example.ui.EditorViewModelFactory
import com.example.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val database = AppDatabase.getDatabase(this)
    val repository = ProjectRepository(database.codeProjectDao())
    val sharedPrefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    com.example.ui.LanguageManager.currentLanguage.value = sharedPrefs.getString("language", "ru") ?: "ru"

    setContent {
      var isDarkTheme by remember { mutableStateOf(true) } // Default to dark theme for IDE feel
      var importedPluginContent: String? by remember { mutableStateOf(null) }

      val handleIntent = { incomingIntent: android.content.Intent ->
          if (incomingIntent.action == android.content.Intent.ACTION_VIEW) {
              incomingIntent.data?.let { uri ->
                  try {
                      val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                      if (content != null) {
                          importedPluginContent = content
                      }
                  } catch(e: Exception) { }
                  incomingIntent.data = null // prevent re-importing
              }
          }
      }
      
      handleIntent(intent)

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val mainViewModel: CodeGramViewModel = viewModel(factory = CodeGramViewModelFactory(repository))

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    if (importedPluginContent != null) {
                        LaunchedEffect(importedPluginContent) {
                            var name = "Imported Plugin"
                            var author = "@unknown"
                            var desc = "No description"
                            val nameMatch = "__name__\\s*=\\s*['\"]([^'\"]+)['\"]".toRegex().find(importedPluginContent!!)
                            if (nameMatch != null) name = nameMatch.groupValues[1]
                            val authorMatch = "__author__\\s*=\\s*['\"]([^'\"]+)['\"]".toRegex().find(importedPluginContent!!)
                            if (authorMatch != null) author = authorMatch.groupValues[1]
                            val descMatch = "__description__\\s*=\\s*['\"]([^'\"]+)['\"]".toRegex().find(importedPluginContent!!)
                            if (descMatch != null) desc = descMatch.groupValues[1]
                            
                            mainViewModel.importProject(name, author, desc, importedPluginContent!!) {
                                android.widget.Toast.makeText(this@MainActivity, "Плагин '$name' импортирован", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            importedPluginContent = null
                        }
                    }

                    HomeScreen(
                        viewModel = mainViewModel,
                        onNavigateToEditor = { projectId ->
                            navController.navigate("editor/$projectId")
                        }
                    )
                }
                composable("editor/{projectId}") { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toIntOrNull() ?: 0
                    val editorViewModel: EditorViewModel = viewModel(
                        key = "editor_$projectId",
                        factory = EditorViewModelFactory(repository, projectId) // Fix logic later to inject context/db
                    )
                    
                    EditorScreen(
                        viewModel = editorViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = { isDarkTheme = it }
                    )
                }
            }
        }
      }
    }
  }
}

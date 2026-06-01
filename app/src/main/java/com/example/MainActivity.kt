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

    setContent {
      var isDarkTheme by remember { mutableStateOf(true) } // Default to dark theme for IDE feel
      
      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val mainViewModel: CodeGramViewModel = viewModel(factory = CodeGramViewModelFactory(repository))

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
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

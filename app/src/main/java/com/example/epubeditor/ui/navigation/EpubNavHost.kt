package com.example.epubeditor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.epubeditor.ui.screens.editor.EditorScreen
import com.example.epubeditor.ui.screens.editor.EditorViewModel
import com.example.epubeditor.ui.screens.home.HomeScreen
import com.example.epubeditor.ui.screens.home.HomeViewModel

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{bookId}"
    fun editor(bookId: String) = "editor/$bookId"
}

@Composable
fun EpubNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onOpenBook = { book ->
                    navController.navigate(Routes.editor(book.id))
                }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            val viewModel: EditorViewModel = hiltViewModel()
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

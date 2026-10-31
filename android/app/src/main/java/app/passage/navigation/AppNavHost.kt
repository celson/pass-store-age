package app.passage.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.passage.feature.detail.ui.SecretDetailScreen
import app.passage.feature.detail.ui.SecretDetailViewModel
import app.passage.feature.editor.ui.SecretEditorScreen
import app.passage.feature.editor.ui.SecretEditorViewModel
import app.passage.feature.sync.ui.SyncScreen
import app.passage.feature.sync.ui.SyncViewModel
import app.passage.feature.vault.ui.VaultScreen
import app.passage.feature.vault.ui.VaultViewModel
import org.koin.androidx.compose.koinViewModel
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Vault : Screen("vault")
    data object Sync : Screen("sync")

    data object SecretDetail : Screen("secret_detail/{path}") {
        fun createRoute(relativePath: String): String =
            "secret_detail/${URLEncoder.encode(relativePath, "UTF-8")}"
    }

    data object SecretEditor : Screen("secret_editor?folder={folder}&path={path}") {
        fun createRoute(folder: String = "", existingPath: String? = null): String {
            val encodedFolder = URLEncoder.encode(folder, "UTF-8")
            val encodedPath = existingPath?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
            return "secret_editor?folder=$encodedFolder&path=$encodedPath"
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Vault.route
    ) {
        // Vault Screen
        composable(Screen.Vault.route) {
            val viewModel: VaultViewModel = koinViewModel()
            VaultScreen(
                onSecretClick = { relativePath ->
                    navController.navigate(Screen.SecretDetail.createRoute(relativePath))
                },
                onAddSecretClick = { currentFolder ->
                    navController.navigate(Screen.SecretEditor.createRoute(folder = currentFolder))
                },
                onSyncClick = {
                    navController.navigate(Screen.Sync.route)
                },
                viewModel = viewModel
            )
        }

        // Secret Detail Screen
        composable(
            route = Screen.SecretDetail.route,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val relativePath = URLDecoder.decode(encodedPath, "UTF-8")
            val viewModel: SecretDetailViewModel = koinViewModel()

            SecretDetailScreen(
                relativePath = relativePath,
                onBackClick = { navController.popBackStack() },
                onEditClick = { path ->
                    navController.navigate(Screen.SecretEditor.createRoute(existingPath = path))
                },
                viewModel = viewModel
            )
        }

        // Secret Editor Screen (Create or Edit)
        composable(
            route = Screen.SecretEditor.route,
            arguments = listOf(
                navArgument("folder") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val folder = URLDecoder.decode(backStackEntry.arguments?.getString("folder") ?: "", "UTF-8")
            val pathArg = backStackEntry.arguments?.getString("path") ?: ""
            val existingPath = if (pathArg.isNotEmpty()) URLDecoder.decode(pathArg, "UTF-8") else null

            val viewModel: SecretEditorViewModel = koinViewModel()

            SecretEditorScreen(
                folder = folder,
                existingSecretPath = existingPath,
                onCloseClick = { navController.popBackStack() },
                onSavedSuccess = {
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }

        // Sync & Onboarding Screen
        composable(Screen.Sync.route) {
            val viewModel: SyncViewModel = koinViewModel()
            SyncScreen(
                onBackClick = { navController.popBackStack() },
                onCloneSuccess = {
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }
    }
}

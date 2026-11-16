package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProPlayerScreen
import com.example.ui.screens.VaultScreen
import kotlinx.serialization.Serializable

@Serializable
data object VaultRoute

@Serializable
data class EditorRoute(val projectId: String)

@Serializable
data class PlayerRoute(val mediaUri: String? = null)

@Composable
fun CutForgeNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = VaultRoute
    ) {
        composable<VaultRoute> {
            VaultScreen(
                onOpenProject = { projectId ->
                    navController.navigate(EditorRoute(projectId))
                },
                onOpenPlayer = { uri ->
                    navController.navigate(PlayerRoute(uri?.let { Uri.encode(it) }))
                }
            )
        }
        composable<EditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditorRoute>()
            EditorScreen(
                projectId = route.projectId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<PlayerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayerRoute>()
            ProPlayerScreen(
                initialUriString = route.mediaUri,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenInEditor = { projectId ->
                    navController.navigate(EditorRoute(projectId)) {
                        popUpTo(VaultRoute)
                    }
                }
            )
        }
    }
}

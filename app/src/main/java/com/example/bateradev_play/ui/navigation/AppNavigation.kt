package com.example.bateradev_play.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.bateradev_play.ui.screens.*
import java.io.File

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Download : Screen("download", "Download", Icons.Default.Download)
    object DrumRemoval : Screen("drum_removal", "Stems", Icons.Default.MusicNote)
    object MusicGeneration : Screen("music_generation", "Gerar IA", Icons.Default.AutoAwesome)
    object Practice : Screen("practice", "Praticar", Icons.Default.PlayCircle)
    object Setlists : Screen("setlists", "Setlists", Icons.Default.ViewList)
    object Files : Screen("files", "Arquivos", Icons.Default.Folder)
    
    // Telas secundárias (não aparecem na bottom nav)
    object Analysis : Screen("analysis", "Analisar", Icons.Default.Analytics)
    object PracticeWithSong : Screen("practice/{songId}", "Praticar", Icons.Default.PlayCircle) {
        fun createRoute(songId: String) = "practice/$songId"
    }
}

// Itens da bottom navigation bar
val bottomNavItems = listOf(
    Screen.Download,
    Screen.DrumRemoval,
    Screen.MusicGeneration,
    Screen.Practice,
    Screen.Setlists,
    Screen.Files
)

// Estado compartilhado para arquivo baixado
object DownloadedFileState {
    var pendingFile: File? = null
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Download.route
) {
    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // Download de músicas
            composable(Screen.Download.route) {
                DownloadScreen(
                    onNavigateToDrumRemoval = { file ->
                        // Salvar arquivo para ser usado na DrumRemovalScreen
                        DownloadedFileState.pendingFile = file
                        navController.navigate(Screen.DrumRemoval.route)
                    }
                )
            }
            
            // Separação de stems
            composable(Screen.DrumRemoval.route) {
                // Pegar arquivo pendente se houver
                val pendingFile = remember { DownloadedFileState.pendingFile }
                
                // Limpar após usar (usar LaunchedEffect para não limpar durante recomposição)
                LaunchedEffect(Unit) {
                    DownloadedFileState.pendingFile = null
                }
                
                DrumRemovalScreen(
                    preselectedFile = pendingFile
                )
            }
            
            // 🆕 Geração de música com IA
            composable(Screen.MusicGeneration.route) {
                MusicGenerationScreen(
                    onMusicGenerated = { url ->
                        // Navegar para tela de prática ou mostrar opções
                        // Por enquanto apenas mostra o resultado
                    }
                )
            }
            
            // Tela de prática (sem música específica)
            composable(Screen.Practice.route) {
                PracticeScreen(songId = null)
            }
            
            // Tela de prática com música específica
            composable(
                route = Screen.PracticeWithSong.route,
                arguments = listOf(
                    navArgument("songId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getString("songId")
                PracticeScreen(songId = songId)
            }
            
            // Setlists
            composable(Screen.Setlists.route) {
                SetlistScreen(
                    onNavigateToSong = { songId ->
                        navController.navigate(Screen.PracticeWithSong.createRoute(songId))
                    }
                )
            }
            
            // Análise de áudio
            composable(Screen.Analysis.route) {
                AnalysisScreen()
            }
            
            // Gerenciador de arquivos
            composable(Screen.Files.route) {
                FilesScreen(
                    onNavigateToPractice = { songId ->
                        navController.navigate(Screen.PracticeWithSong.createRoute(songId))
                    }
                )
            }
        }
    }
}

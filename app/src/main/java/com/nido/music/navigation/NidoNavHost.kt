package com.nido.music.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nido.music.feature.clipshare.ui.ClipTrimmerScreen
import com.nido.music.feature.discover.ui.DiscoverScreen
import com.nido.music.feature.music.ui.library.LibraryScreen
import com.nido.music.feature.music.ui.player.PlayerScreen
import com.nido.music.feature.settings.ui.SettingsScreen

/**
 * Root navigation graph for Nido.
 * Top-level destinations are library, discover, settings.
 * Nested: player (full-screen), clip trimmer, album/artist detail.
 */
@Composable
fun NidoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.LIBRARY.route,
        modifier = modifier
    ) {
        // Top-level
        composable(TopLevelDestination.LIBRARY.route) {
            LibraryScreen(
                onNavigateToPlayer = { navController.navigate("player") },
                onNavigateToAlbum = { album -> navController.navigate("album/$album") },
                onNavigateToArtist = { artist -> navController.navigate("artist/$artist") }
            )
        }

        composable(TopLevelDestination.DISCOVER.route) {
            DiscoverScreen()
        }

        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen()
        }

        // Nested destinations
        composable("player") {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToClipTrimmer = { songId ->
                    navController.navigate("clip_trimmer/$songId")
                }
            )
        }

        composable("clip_trimmer/{songId}") { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId")?.toLongOrNull() ?: return@composable
            ClipTrimmerScreen(
                songId = songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("album/{albumName}") { backStackEntry ->
            val albumName = backStackEntry.arguments?.getString("albumName") ?: return@composable
            // TODO: AlbumDetailScreen — Phase 2
        }

        composable("artist/{artistName}") { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
            // TODO: ArtistDetailScreen — Phase 2
        }
    }
}

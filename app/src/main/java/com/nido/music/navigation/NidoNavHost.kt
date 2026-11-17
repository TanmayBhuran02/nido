package com.nido.music.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.nido.music.feature.chat.ui.ConversationsViewModel
import com.nido.music.feature.clipshare.ui.ClipTrimmerScreen
import com.nido.music.feature.chat.ui.ConversationsScreen
import com.nido.music.feature.chat.ui.ChatDetailScreen
import com.nido.music.feature.chat.ui.RegisterScreen
import com.nido.music.feature.discover.ui.DiscoverScreen
import com.nido.music.feature.music.ui.library.LibraryScreen
import com.nido.music.feature.music.ui.player.PlayerScreen
import com.nido.music.feature.settings.ui.SettingsScreen

/**
 * Root navigation graph for Nido.
 * Top-level destinations are library, chat, discover, settings.
 * Nested: player (full-screen), clip trimmer, album/artist detail, chat detail.
 */
@Composable
fun NidoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String? = null
) {
    val context = LocalContext.current
    val actualStartDestination = remember {
        startDestination ?: run {
            val prefs = context.getSharedPreferences("nido_chat_auth_prefs", Context.MODE_PRIVATE)
            val hasCompleted = prefs.getBoolean("has_completed_onboarding", false) ||
                    (!prefs.getString("user_id", null).isNullOrEmpty() && !prefs.getString("access_token", null).isNullOrEmpty())
            if (hasCompleted) TopLevelDestination.LIBRARY.route else "register"
        }
    }

    NavHost(
        navController = navController,
        startDestination = actualStartDestination,
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

        composable(TopLevelDestination.CHAT.route) {
            ConversationsScreen(
                onNavigateToChat = { convId -> navController.navigate("chat/$convId") },
                onNavigateToRegister = { navController.navigate("register") }
            )
        }

        composable("register") {
            val isFromOnboarding = remember {
                navController.previousBackStackEntry == null
            }
            RegisterScreen(
                onRegistered = {
                    if (isFromOnboarding) {
                        navController.navigate(TopLevelDestination.LIBRARY.route) {
                            popUpTo("register") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onNavigateBack = {
                    if (isFromOnboarding) {
                        navController.navigate(TopLevelDestination.LIBRARY.route) {
                            popUpTo("register") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onSkip = if (isFromOnboarding) {
                    {
                        navController.navigate(TopLevelDestination.LIBRARY.route) {
                            popUpTo("register") { inclusive = true }
                        }
                    }
                } else null
            )
        }

        composable(TopLevelDestination.DISCOVER.route) {
            DiscoverScreen()
        }

        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen()
        }

        // Nested destinations
        composable("chat/{conversationId}") { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatDetailScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToClipTrimmer = {
                    navController.navigate("clip_trimmer/1")
                }
            )
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
            val conversationsViewModel: ConversationsViewModel = hiltViewModel()
            ClipTrimmerScreen(
                songId = songId,
                onNavigateBack = { navController.popBackStack() },
                onShareToChat = { peerId, peerTitle, clipTitle, artist, startMs, endMs, filePath ->
                    conversationsViewModel.shareClipToPeer(
                        peerId = peerId,
                        peerTitle = peerTitle,
                        clipTitle = clipTitle,
                        artist = artist,
                        startMs = startMs,
                        endMs = endMs,
                        filePath = filePath
                    ) { convId ->
                        navController.navigate("chat/$convId") {
                            popUpTo("clip_trimmer/{songId}") { inclusive = true }
                        }
                    }
                }
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

package com.navink

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navink.data.repository.SettingsRepository
import com.navink.ui.browse.AlbumsScreen
import com.navink.ui.browse.ArtistsScreen
import com.navink.ui.browse.BrowseViewModel
import com.navink.ui.browse.SongsScreen
import com.navink.ui.downloads.DownloadsScreen
import com.navink.ui.favourites.FavouritesScreen
import com.navink.ui.player.MiniPlayer
import com.navink.ui.player.NowPlayingScreen
import com.navink.ui.player.PlayerViewModel
import com.navink.ui.search.SearchScreen
import com.navink.ui.settings.SettingsScreen
import kotlinx.coroutines.runBlocking

@Composable
fun NavGraph(
    settingsRepository: SettingsRepository,
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val browseViewModel: BrowseViewModel = hiltViewModel()
    val creds = remember { runBlocking { settingsRepository.getCredentials() } }

    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(onTap = { navController.navigate("nowplaying") }, viewModel = playerViewModel)
    }

    val startDestination = if (creds.hasCredentials) "browse/artists" else "settings"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("settings") {
            SettingsScreen(onConnected = {
                navController.navigate("browse/artists") {
                    popUpTo("settings") { inclusive = true }
                }
            })
        }

        composable("browse/artists") {
            ArtistsScreen(
                onArtistClick = { artistId -> navController.navigate("browse/albums/$artistId") },
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToDownloads = { navController.navigate("downloads") },
                onNavigateToSettings = { navController.navigate("settings/edit") },
                miniPlayer = miniPlayer,
                viewModel = browseViewModel,
            )
        }

        composable(
            "browse/albums/{artistId}",
            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
        ) { back ->
            val artistId = back.arguments!!.getString("artistId")!!
            AlbumsScreen(
                artistId = artistId,
                onAlbumClick = { albumId -> navController.navigate("browse/songs/$albumId") },
                onBack = { navController.navigateUp() },
                miniPlayer = miniPlayer,
                viewModel = browseViewModel,
            )
        }

        composable(
            "browse/songs/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) { back ->
            val albumId = back.arguments!!.getString("albumId")!!
            SongsScreen(
                albumId = albumId,
                onSongClick = { songId, aId ->
                    playerViewModel.playSongFromAlbum(songId, aId)
                    navController.navigate("nowplaying")
                },
                onBack = { navController.navigateUp() },
                miniPlayer = miniPlayer,
                viewModel = browseViewModel,
            )
        }

        composable("search") {
            SearchScreen(
                onSongClick = { songId, albumId ->
                    playerViewModel.playSongFromAlbum(songId, albumId)
                    navController.navigate("nowplaying")
                },
                onAlbumClick = { albumId -> navController.navigate("browse/songs/$albumId") },
                onArtistClick = { artistId -> navController.navigate("browse/albums/$artistId") },
                onBack = { navController.navigateUp() },
                miniPlayer = miniPlayer,
            )
        }

        composable("favourites") {
            FavouritesScreen(
                onSongClick = { songId, albumId ->
                    playerViewModel.playSongFromAlbum(songId, albumId)
                    navController.navigate("nowplaying")
                },
                miniPlayer = miniPlayer,
            )
        }

        composable("downloads") {
            DownloadsScreen(
                onSongClick = { songId, albumId ->
                    playerViewModel.playSongFromAlbum(songId, albumId)
                    navController.navigate("nowplaying")
                },
                onBack = { navController.navigateUp() },
                miniPlayer = miniPlayer,
            )
        }

        composable("nowplaying") {
            NowPlayingScreen(
                onBack = { navController.navigateUp() },
                viewModel = playerViewModel,
            )
        }

        composable("settings/edit") {
            SettingsScreen(
                onConnected = { navController.navigateUp() },
                onBack = { navController.navigateUp() },
            )
        }
    }
}

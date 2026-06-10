package com.navink.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navink.data.local.entity.ArtistEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    onArtistClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.observeArtists()
        if (!state.isOfflineMode) viewModel.syncOnLaunch()
    }

    val displayedArtists = if (state.isOfflineMode) {
        val offlineArtistIds = state.downloadedSongs.map { it.artistId }.toSet()
        state.artists.filter { it.id in offlineArtistIds }
    } else {
        state.artists
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    TextButton(onClick = { viewModel.toggleOfflineMode() }) {
                        Text(if (state.isOfflineMode) "Online" else "Offline")
                    }
                    if (!state.isOfflineMode) {
                        TextButton(onClick = onNavigateToSearch) { Text("🔍") }
                        TextButton(onClick = onNavigateToDownloads) { Text("↓") }
                        if (!state.isSyncing) {
                            TextButton(onClick = { viewModel.syncOnLaunch() }) { Text("↻") }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        if (!state.isOfflineMode && state.isSyncing && state.artists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Syncing library…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (state.syncError != null && state.artists.isEmpty()) {
                    item {
                        Text(
                            text = "Cannot sync: ${state.syncError}",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (state.downloadMessage != null) {
                    item {
                        Text(
                            text = state.downloadMessage!!,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val offlineAlbumCountByArtist = if (state.isOfflineMode) {
                    state.downloadedSongs
                        .groupBy { it.artistId }
                        .mapValues { (_, songs) -> songs.map { it.albumId }.toSet().size }
                } else emptyMap()
                items(displayedArtists, key = { it.id }) { artist ->
                    ArtistRow(
                        artist = artist,
                        albumCount = offlineAlbumCountByArtist[artist.id] ?: artist.albumCount,
                        onClick = { onArtistClick(artist.id) },
                        onLongClick = { if (!state.isOfflineMode) viewModel.downloadArtist(artist.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(artist: ArtistEntity, albumCount: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = "$albumCount albums", style = MaterialTheme.typography.bodySmall)
        }
    }
}

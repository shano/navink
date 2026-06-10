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
import com.navink.data.local.entity.AlbumEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    artistId: String,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(artistId) {
        viewModel.observeAlbums(artistId)
        if (!state.isOfflineMode) viewModel.syncArtistAlbums(artistId)
    }

    val displayedAlbums = if (state.isOfflineMode) {
        val offlineAlbumIds = state.downloadedSongs
            .filter { it.artistId == artistId }
            .map { it.albumId }.toSet()
        state.albums.filter { it.id in offlineAlbumIds }
    } else {
        state.albums
    }

    LaunchedEffect(state.downloadMessage) {
        if (state.downloadMessage != null) {
            delay(3000)
            viewModel.clearDownloadMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.downloadMessage ?: "Albums")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        when {
            state.isLoadingAlbums && state.albums.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    if (displayedAlbums.isEmpty() && state.albumSyncError != null) {
                        item {
                            Text(
                                text = "Sync error: ${state.albumSyncError}",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(displayedAlbums, key = { it.id }) { album ->
                        AlbumRow(
                            album = album,
                            onClick = { onAlbumClick(album.id) },
                            onLongClick = { viewModel.downloadAlbum(album.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(album: AlbumEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            Text(text = album.name, style = MaterialTheme.typography.bodyLarge)
            album.year?.let { Text(text = it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

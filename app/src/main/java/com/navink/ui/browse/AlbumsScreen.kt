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

@Composable
fun AlbumsScreen(
    artistId: String,
    onAlbumClick: (String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(artistId) { viewModel.observeAlbums(artistId) }

    if (state.downloadMessage != null) {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(title = {
                    Text(state.downloadMessage!!, style = MaterialTheme.typography.bodySmall)
                })
            },
            bottomBar = miniPlayer,
        ) { padding ->
            AlbumList(
                albums = state.albums,
                onAlbumClick = onAlbumClick,
                onDownloadAlbum = { viewModel.downloadAlbum(it) },
                padding = padding,
            )
        }
    } else {
        Scaffold(bottomBar = miniPlayer) { padding ->
            AlbumList(
                albums = state.albums,
                onAlbumClick = onAlbumClick,
                onDownloadAlbum = { viewModel.downloadAlbum(it) },
                padding = padding,
            )
        }
    }
}

@Composable
private fun AlbumList(
    albums: List<AlbumEntity>,
    onAlbumClick: (String) -> Unit,
    onDownloadAlbum: (String) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        items(albums, key = { it.id }) { album ->
            AlbumRow(
                album = album,
                onClick = { onAlbumClick(album.id) },
                onLongClick = { onDownloadAlbum(album.id) },
            )
            HorizontalDivider()
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

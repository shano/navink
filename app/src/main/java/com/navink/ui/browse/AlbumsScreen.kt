package com.navink.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.navink.data.local.entity.AlbumEntity

@Composable
fun AlbumsScreen(
    artistId: String,
    coverArtUrl: (String?) -> String?,
    onAlbumClick: (String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(artistId) { viewModel.observeAlbums(artistId) }

    Scaffold(bottomBar = miniPlayer) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.albums, key = { it.id }) { album ->
                AlbumRow(
                    album = album,
                    coverArtUrl = coverArtUrl(album.coverArtId),
                    onClick = { onAlbumClick(album.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumEntity, coverArtUrl: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverArtUrl)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = album.name, style = MaterialTheme.typography.bodyLarge)
            album.year?.let { Text(text = it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

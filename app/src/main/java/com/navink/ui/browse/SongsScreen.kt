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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navink.data.local.entity.SongEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    albumId: String,
    onSongClick: (songId: String, albumId: String) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(albumId) {
        if (!state.isOfflineMode) viewModel.observeSongs(albumId)
    }

    val displayedSongs = if (state.isOfflineMode) {
        state.downloadedSongs.filter { it.albumId == albumId }
    } else {
        state.songs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Songs") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(displayedSongs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song.id, albumId) },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun Int.toMinSec(): String {
    val m = this / 60
    val s = this % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun SongRow(song: SongEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        song.trackNumber?.let {
            Text(
                text = it.toString().padStart(2),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(28.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = song.duration.toMinSec(), style = MaterialTheme.typography.bodySmall)
        if (song.isDownloaded) {
            Spacer(Modifier.width(4.dp))
            Text(text = "↓", style = MaterialTheme.typography.bodySmall)
        }
    }
}

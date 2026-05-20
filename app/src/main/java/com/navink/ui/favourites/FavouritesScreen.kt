package com.navink.ui.favourites

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

@Composable
fun FavouritesScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(bottomBar = miniPlayer) { padding ->
        if (state.songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No starred songs yet")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.songs, key = { it.id }) { song ->
                    StarredSongRow(
                        song = song,
                        onTap = { onSongClick(song.id, song.albumId) },
                        onStarToggle = { viewModel.toggleStar(song.id, song.isStarred) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StarredSongRow(song: SongEntity, onTap: () -> Unit, onStarToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onTap)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onStarToggle) {
            Text(if (song.isStarred) "★" else "☆")
        }
    }
}

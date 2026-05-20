package com.navink.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun SearchScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(bottomBar = miniPlayer) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            TextFieldMMD(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            )
            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn {
                    if (state.artists.isNotEmpty()) {
                        item {
                            Text(
                                "Artists",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(state.artists) { artist ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onArtistClick(artist.id) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { Text(artist.name) }
                            HorizontalDivider()
                        }
                    }
                    if (state.albums.isNotEmpty()) {
                        item {
                            Text(
                                "Albums",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(state.albums) { album ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onAlbumClick(album.id) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { Text(album.name) }
                            HorizontalDivider()
                        }
                    }
                    if (state.songs.isNotEmpty()) {
                        item {
                            Text(
                                "Songs",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(state.songs) { song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onSongClick(song.id, song.albumId) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { Text(song.title) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

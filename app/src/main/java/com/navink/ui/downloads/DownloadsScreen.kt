package com.navink.ui.downloads

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
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.navink.data.local.entity.SongEntity
import com.navink.ui.browse.BrowseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStorageLocation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                StorageToggle(
                    location = state.storageLocation,
                    onSelect = viewModel::setStorageLocation,
                )
                HorizontalDivider()
            }
            if (state.downloadedSongs.isEmpty()) {
                item {
                    Text(
                        text = "No downloaded tracks. Long-press an artist or album to download.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.downloadedSongs, key = { it.id }) { song ->
                    DownloadedSongRow(
                        song = song,
                        onClick = { onSongClick(song.id, song.albumId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StorageToggle(location: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Save to:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp),
        )
        if (location == "external") {
            ButtonMMD(
                onClick = { onSelect("external") },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("SD Card") }
            OutlinedButtonMMD(
                onClick = { onSelect("internal") },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Internal") }
        } else {
            OutlinedButtonMMD(
                onClick = { onSelect("external") },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("SD Card") }
            ButtonMMD(
                onClick = { onSelect("internal") },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Internal") }
        }
    }
}

@Composable
private fun DownloadedSongRow(song: SongEntity, onClick: () -> Unit) {
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
        Column(Modifier.weight(1f)) {
            Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = "↓", style = MaterialTheme.typography.bodySmall)
    }
}

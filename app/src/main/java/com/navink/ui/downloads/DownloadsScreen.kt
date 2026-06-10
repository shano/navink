package com.navink.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.navink.download.DownloadWorker
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
            if (state.downloadQueue.isEmpty()) {
                item {
                    Text(
                        text = "No active downloads. Long-press an artist or album to queue downloads.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.downloadQueue, key = { it.id }) { workInfo ->
                    QueueItemRow(workInfo = workInfo)
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
private fun QueueItemRow(workInfo: WorkInfo) {
    val prefix = "${DownloadWorker.KEY_SONG_TITLE}:"
    val title = workInfo.tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix) ?: "Track"
    val stateLabel = if (workInfo.state == WorkInfo.State.RUNNING) "Downloading…" else "Queued"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = stateLabel, style = MaterialTheme.typography.bodySmall)
    }
}

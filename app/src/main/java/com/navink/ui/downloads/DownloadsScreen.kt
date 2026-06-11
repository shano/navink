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
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.navink.data.local.entity.DownloadQueueEntity
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
    val hasFailed = state.downloadQueue.any { it.status == DownloadQueueEntity.STATUS_FAILED }

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
            if (state.downloadQueue.isEmpty()) {
                item {
                    Text(
                        text = "No active downloads. Long-press an artist or album to queue downloads, or use ↓ All on an album.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.downloadQueue, key = { it.songId }) { item ->
                    QueueItemRow(item = item)
                    HorizontalDivider()
                }
                if (hasFailed) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButtonMMD(
                                onClick = { viewModel.retryFailedDownloads() },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Retry failed") }
                            OutlinedButtonMMD(
                                onClick = { viewModel.clearFailedDownloads() },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Clear failed") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(item: DownloadQueueEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            if (item.status == DownloadQueueEntity.STATUS_FAILED && item.errorMessage != null) {
                Text(text = item.errorMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = when (item.status) {
                DownloadQueueEntity.STATUS_RUNNING -> "${item.progressPercent}%"
                DownloadQueueEntity.STATUS_FAILED -> "Failed"
                else -> "Queued"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

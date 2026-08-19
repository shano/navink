package com.navink.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.slider.SliderMMD
import java.util.Locale

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isDownloading by viewModel.isDownloadingCurrentSong.collectAsState()
    val isDownloaded by viewModel.isCurrentSongDownloaded.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text("← Back")
        }
        Spacer(Modifier.weight(1f))

        Text(
            text = state.currentTitle.ifBlank { "—" },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.currentArtist,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = state.currentAlbum,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.weight(1f))

        SliderMMD(
            value = dragFraction ?: playedFraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { viewModel.seekTo(it) }
                dragFraction = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatMs(positionMs), style = MaterialTheme.typography.bodySmall)
            Text(text = formatMs(durationMs), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButtonMMD(
                onClick = { viewModel.previous() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text("⏮") }

            ButtonMMD(
                onClick = { viewModel.playPause() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text(if (state.isPlaying) "⏸" else "▶") }

            OutlinedButtonMMD(
                onClick = { viewModel.next() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text("⏭") }
        }
        Spacer(Modifier.height(12.dp))
        if (state.currentSongId != null) {
            if (!isDownloaded) {
                OutlinedButtonMMD(
                    onClick = { if (!isDownloading) viewModel.downloadCurrentSong() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(if (isDownloading) "Queued…" else "Download")
                }
            } else {
                OutlinedButtonMMD(
                    onClick = { viewModel.deleteCurrentSongDownload() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Remove download")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

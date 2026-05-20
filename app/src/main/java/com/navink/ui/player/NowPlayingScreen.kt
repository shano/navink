package com.navink.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD

@Composable
fun NowPlayingScreen(
    coverArtUrl: String?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text("← Back", color = Color.White)
        }
        Spacer(Modifier.height(24.dp))

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverArtUrl)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = state.currentTitle.ifBlank { "—" },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Text(
            text = state.currentArtist,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Text(
            text = state.currentAlbum,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButtonMMD(
                onClick = { viewModel.previous() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text("⏮", color = Color.White) }

            ButtonMMD(
                onClick = { viewModel.playPause() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text(if (state.isPlaying) "⏸" else "▶") }

            OutlinedButtonMMD(
                onClick = { viewModel.next() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text("⏭", color = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        val currentSongId = state.currentSongId
        if (currentSongId != null) {
            OutlinedButtonMMD(
                onClick = { viewModel.downloadCurrentSong() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Download", color = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

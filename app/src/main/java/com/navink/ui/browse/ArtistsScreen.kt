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
import com.navink.data.local.entity.ArtistEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    onArtistClick: (String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.observeArtists() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    if (!state.isSyncing) {
                        TextButton(onClick = { viewModel.syncOnLaunch() }) { Text("↻") }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        if (state.isSyncing && state.artists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (state.syncError != null && state.artists.isEmpty()) {
                    item {
                        Text(
                            text = "Cannot sync: ${state.syncError}",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(state.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onClick = { onArtistClick(artist.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntity, onClick: () -> Unit) {
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
        Column {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = "${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall)
        }
    }
}

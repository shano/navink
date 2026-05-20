package com.navink.player

data class PlayerState(
    val currentSongId: String? = null,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentAlbum: String = "",
    val currentCoverArtId: String? = null,
    val isPlaying: Boolean = false,
    val hasQueue: Boolean = false,
)

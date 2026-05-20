package com.navink.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubsonicResponse(
    @SerializedName("subsonic-response") val response: SubsonicResponseBody,
)

data class SubsonicResponseBody(
    val status: String,
    val version: String,
    val artists: ArtistsResult? = null,
    val artist: ArtistDetailDto? = null,
    val album: AlbumDetailDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val starred2: Starred2Dto? = null,
    val error: ErrorDto? = null,
)

data class ErrorDto(val code: Int, val message: String)

data class ArtistsResult(val index: List<ArtistIndexDto> = emptyList())

data class ArtistIndexDto(
    val name: String,
    val artist: List<ArtistDto> = emptyList(),
)

data class ArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val starred: String? = null,
)

data class ArtistDetailDto(
    val id: String,
    val name: String,
    val album: List<AlbumDto> = emptyList(),
    val starred: String? = null,
)

data class AlbumDto(
    val id: String,
    val artistId: String = "",
    val name: String,
    val year: Int? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val starred: String? = null,
)

data class AlbumDetailDto(
    val id: String,
    val artistId: String = "",
    val name: String,
    val coverArt: String? = null,
    val starred: String? = null,
    val song: List<SongDto> = emptyList(),
)

data class SongDto(
    val id: String,
    val albumId: String = "",
    val artistId: String = "",
    val title: String,
    val track: Int? = null,
    val duration: Int = 0,
    val coverArt: String? = null,
    val starred: String? = null,
)

data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

data class Starred2Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

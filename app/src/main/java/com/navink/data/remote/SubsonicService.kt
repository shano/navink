package com.navink.data.remote

import com.navink.data.remote.dto.SubsonicResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicService {
    @GET("rest/ping.view")
    suspend fun ping(): SubsonicResponse

    @GET("rest/getArtists.view")
    suspend fun getArtists(): SubsonicResponse

    @GET("rest/getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicResponse

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicResponse

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 20,
    ): SubsonicResponse

    @GET("rest/star.view")
    suspend fun star(
        @Query("id") songId: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicResponse

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("id") songId: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicResponse

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(): SubsonicResponse
}

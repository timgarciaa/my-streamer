package com.mystreamer.tv.data.network

import com.mystreamer.tv.data.model.BrowseResponse
import com.mystreamer.tv.data.model.SubtitleTracksResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface StreamerApi {
    @GET("api/browse")
    suspend fun browse(@Query("path") path: String): BrowseResponse

    @GET("subtitle-tracks")
    suspend fun subtitleTracks(@Query("path") path: String): SubtitleTracksResponse
}

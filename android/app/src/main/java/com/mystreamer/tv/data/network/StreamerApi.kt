package com.mystreamer.tv.data.network

import com.mystreamer.tv.data.model.BrowseResponse
import com.mystreamer.tv.data.model.DurationResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface StreamerApi {
    @GET("api/browse")
    suspend fun browse(@Query("path") path: String): BrowseResponse

    @GET("api/duration")
    suspend fun getDuration(@Query("path") path: String): DurationResponse
}

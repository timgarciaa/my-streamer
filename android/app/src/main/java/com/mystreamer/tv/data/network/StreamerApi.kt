package com.mystreamer.tv.data.network

import com.mystreamer.tv.data.model.BrowseResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface StreamerApi {
    @GET("api/browse")
    suspend fun browse(@Query("path") path: String): BrowseResponse
}

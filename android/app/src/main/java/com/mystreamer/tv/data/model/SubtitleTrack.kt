package com.mystreamer.tv.data.model

import com.google.gson.annotations.SerializedName

data class SubtitleTrack(
    @SerializedName("index") val index: Int,
    @SerializedName("language") val language: String,
    @SerializedName("title") val title: String
)

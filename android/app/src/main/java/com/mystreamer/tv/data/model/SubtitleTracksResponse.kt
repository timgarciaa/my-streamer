package com.mystreamer.tv.data.model

import com.google.gson.annotations.SerializedName

data class SubtitleTracksResponse(
    @SerializedName("tracks") val tracks: List<SubtitleTrack>
)

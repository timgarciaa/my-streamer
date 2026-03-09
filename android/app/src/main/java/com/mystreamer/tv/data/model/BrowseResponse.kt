package com.mystreamer.tv.data.model

import com.google.gson.annotations.SerializedName

data class BrowseResponse(
    @SerializedName("items") val items: List<FileItem>
)

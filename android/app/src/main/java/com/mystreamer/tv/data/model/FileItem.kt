package com.mystreamer.tv.data.model

import com.google.gson.annotations.SerializedName

data class FileItem(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,       // "directory" | "file"
    @SerializedName("ext") val ext: String?,
    @SerializedName("size") val size: Long?,
    @SerializedName("relativePath") val relativePath: String
) {
    val isDirectory: Boolean get() = type == "directory"
    val isVideo: Boolean get() = ext != null && ext.trimStart('.').lowercase() in VIDEO_EXTENSIONS

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts")
    }
}

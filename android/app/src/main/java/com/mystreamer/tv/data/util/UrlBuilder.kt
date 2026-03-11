package com.mystreamer.tv.data.util

import android.net.Uri

object UrlBuilder {

    fun streamUrl(baseUrl: String, relativePath: String): String {
        val base = baseUrl.trimEnd('/')
        val encoded = Uri.encode(relativePath)
        return "$base/stream?path=$encoded"
    }

    fun subtitleUrl(baseUrl: String, relativePath: String, streamIndex: Int? = null): String {
        val base = baseUrl.trimEnd('/')
        val encoded = Uri.encode(relativePath)
        return if (streamIndex != null) "$base/subtitle?path=$encoded&stream=$streamIndex"
        else "$base/subtitle?path=$encoded"
    }

    fun subtitleTracksUrl(baseUrl: String, relativePath: String): String {
        val base = baseUrl.trimEnd('/')
        val encoded = Uri.encode(relativePath)
        return "$base/subtitle-tracks?path=$encoded"
    }
}

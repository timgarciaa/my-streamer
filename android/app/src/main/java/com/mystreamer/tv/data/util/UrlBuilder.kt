package com.mystreamer.tv.data.util

import android.net.Uri

object UrlBuilder {

    fun streamUrl(baseUrl: String, relativePath: String): String {
        val base = baseUrl.trimEnd('/')
        val encoded = Uri.encode(relativePath)
        return "$base/stream?path=$encoded"
    }

    fun subtitleUrl(baseUrl: String, relativePath: String): String {
        val base = baseUrl.trimEnd('/')
        val encoded = Uri.encode(relativePath)
        return "$base/subtitle?path=$encoded"
    }
}

package com.mystreamer.tv.data.prefs

import android.content.Context
import androidx.preference.PreferenceManager

class ServerPreferences(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val DEFAULT_URL = "http://192.168.100.13:3001"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

package com.mystreamer.tv.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.mystreamer.tv.R
import com.mystreamer.tv.data.network.RetrofitClient
import com.mystreamer.tv.data.prefs.ServerPreferences

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.server_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == ServerPreferences.KEY_SERVER_URL) {
            val newUrl = sharedPreferences?.getString(key, ServerPreferences.DEFAULT_URL)
                ?: ServerPreferences.DEFAULT_URL
            RetrofitClient.init(newUrl)
        }
    }
}

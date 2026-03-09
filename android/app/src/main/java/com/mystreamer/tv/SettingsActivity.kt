package com.mystreamer.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.mystreamer.tv.ui.settings.SettingsFragment

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_frame, SettingsFragment())
                .commit()
        }
    }
}

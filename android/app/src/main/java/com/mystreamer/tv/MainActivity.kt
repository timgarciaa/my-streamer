package com.mystreamer.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.mystreamer.tv.data.network.RetrofitClient
import com.mystreamer.tv.data.prefs.ServerPreferences
import com.mystreamer.tv.ui.browse.BrowseFragment

class MainActivity : FragmentActivity() {

    private lateinit var serverPrefs: ServerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverPrefs = ServerPreferences(this)
        RetrofitClient.init(serverPrefs.serverUrl)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, BrowseFragment())
                .commit()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Menu key opens settings
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

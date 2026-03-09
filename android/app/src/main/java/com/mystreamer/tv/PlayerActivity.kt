package com.mystreamer.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import com.mystreamer.tv.ui.player.PlayerFragment

class PlayerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_TITLE = "extra_title"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val fragment = supportFragmentManager
                .findFragmentById(R.id.player_frame) as? PlayerFragment
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> { fragment?.seekForward(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT  -> { fragment?.seekBackward(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        if (savedInstanceState == null) {
            val path = intent.getStringExtra(EXTRA_PATH) ?: ""
            val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

            val fragment = PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(PlayerFragment.ARG_PATH, path)
                    putString(PlayerFragment.ARG_TITLE, title)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.player_frame, fragment)
                .commit()
        }
    }
}

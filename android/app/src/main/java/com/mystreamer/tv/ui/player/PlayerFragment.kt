package com.mystreamer.tv.ui.player

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.mystreamer.tv.R
import com.mystreamer.tv.data.prefs.ServerPreferences
import com.mystreamer.tv.data.util.UrlBuilder

@UnstableApi
class PlayerFragment : Fragment() {

    companion object {
        const val ARG_PATH = "path"
        const val ARG_TITLE = "title"
        private const val SEEK_INCREMENT_MS = 10_000L
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var serverPrefs: ServerPreferences

    private var filePath: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serverPrefs = ServerPreferences(requireContext())
        filePath = arguments?.getString(ARG_PATH) ?: ""
        val title = arguments?.getString(ARG_TITLE) ?: ""

        playerView = view.findViewById(R.id.player_view)
        playerView.useController = true
        playerView.controllerAutoShow = true
        playerView.controllerHideOnTouch = true
        playerView.controllerShowTimeoutMs = 3000

        val trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
            )
        }
        player = ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
            .build()
        playerView.player = player

        // Wire up custom control buttons from player_controls.xml
        playerView.findViewById<TextView>(R.id.video_title)?.text = title
        playerView.findViewById<Button>(R.id.rewind_button)?.setOnClickListener { seekBackward() }
        playerView.findViewById<Button>(R.id.forward_button)?.setOnClickListener { seekForward() }
        playerView.findViewById<Button>(R.id.prev_button)?.isEnabled = false
        playerView.findViewById<Button>(R.id.next_button)?.isEnabled = false

        loadMedia()
    }

    private fun loadMedia() {
        val baseUrl = serverPrefs.serverUrl
        val streamUrl = UrlBuilder.streamUrl(baseUrl, filePath)
        val subtitleUrl = UrlBuilder.subtitleUrl(baseUrl, filePath)

        val subtitleConfig = SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun seekForward() {
        val newPos = player.currentPosition + SEEK_INCREMENT_MS
        player.seekTo(newPos.coerceAtMost(player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE))
        showSeekIndicator(forward = true)
    }

    fun seekBackward() {
        val newPos = (player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0)
        player.seekTo(newPos)
        showSeekIndicator(forward = false)
    }

    private fun showSeekIndicator(forward: Boolean) {
        val container = playerView.findViewById<View>(R.id.seek_indicators) ?: return
        val backView = playerView.findViewById<View>(R.id.seek_backward_indicator)
        val fwdView = playerView.findViewById<View>(R.id.seek_forward_indicator)

        backView?.visibility = if (!forward) View.VISIBLE else View.GONE
        fwdView?.visibility = if (forward) View.VISIBLE else View.GONE

        container.animate().cancel()
        container.alpha = 1f
        container.animate()
            .alpha(0f)
            .setStartDelay(800)
            .setDuration(400)
            .start()
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player.release()
    }
}

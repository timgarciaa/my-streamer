package com.mystreamer.tv.ui.player

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mystreamer.tv.R
import com.mystreamer.tv.data.network.RetrofitClient
import com.mystreamer.tv.data.prefs.ServerPreferences
import com.mystreamer.tv.data.util.UrlBuilder
import kotlinx.coroutines.launch

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
    private var startPositionSec: Long = 0L
    private var cachedDurationMs: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serverPrefs = ServerPreferences(requireContext())
        filePath = arguments?.getString(ARG_PATH) ?: ""

        playerView = view.findViewById(R.id.player_view)
        playerView.useController = true
        playerView.controllerAutoShow = true
        playerView.controllerHideOnTouch = false
        playerView.controllerShowTimeoutMs = 3000

        playerView.setShowPreviousButton(false)
        playerView.setShowNextButton(false)
        playerView.setShowFastForwardButton(false)
        playerView.setShowRewindButton(false)

        player = ExoPlayer.Builder(requireContext()).build()

        viewLifecycleOwner.lifecycleScope.launch {
            cachedDurationMs = try {
                RetrofitClient.api.getDuration(filePath).durationMs
            } catch (e: Exception) { 0L }

            val wrappedPlayer = DurationOverridePlayer(player, cachedDurationMs)
            playerView.player = wrappedPlayer
            loadMedia(startPositionSec)
        }
    }

    private fun loadMedia(startSec: Long) {
        val baseUrl = serverPrefs.serverUrl
        val streamUrl = UrlBuilder.streamUrl(baseUrl, filePath, startSec)
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
        val currentPosSec = player.currentPosition / 1000L
        val newStartSec = currentPosSec + (SEEK_INCREMENT_MS / 1000L)
        restartAt(newStartSec)
    }

    fun seekBackward() {
        val currentPosSec = player.currentPosition / 1000L
        val newStartSec = maxOf(0L, currentPosSec - (SEEK_INCREMENT_MS / 1000L))
        restartAt(newStartSec)
    }

    private fun restartAt(startSec: Long) {
        startPositionSec = startSec
        player.stop()
        player.clearMediaItems()
        loadMedia(startSec)
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

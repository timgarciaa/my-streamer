package com.mystreamer.tv.ui.player

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.mystreamer.tv.R
import androidx.media3.ui.R as MediaR
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import com.mystreamer.tv.data.model.SubtitleTrack
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
    private lateinit var trackSelector: DefaultTrackSelector
    private var availableSubtitleTracks: List<SubtitleTrack> = emptyList()
    private var textTrackGroups: List<Tracks.Group> = emptyList()
    private var userDisabledSubtitles = false

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
        playerView.controllerShowTimeoutMs = 5000

        trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
            )
        }
        player = ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
            .build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                textTrackGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (!userDisabledSubtitles) forceTextTrackIfNoneSelected(tracks)
                updateCcButtonState()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerView.keepScreenOn = isPlaying
                val color = if (isPlaying) 0xFFE94560.toInt() else 0xFFFFFFFF.toInt()
                playerView.findViewById<ImageButton>(MediaR.id.exo_play_pause)
                    ?.imageTintList = ColorStateList.valueOf(color)
            }
        })

        // Wire up custom control buttons from player_controls.xml
        playerView.findViewById<TextView>(R.id.video_title)?.text = title
        playerView.findViewById<Button>(R.id.rewind_button)?.setOnClickListener { seekBackward() }
        playerView.findViewById<Button>(R.id.forward_button)?.setOnClickListener { seekForward() }
playerView.findViewById<Button>(R.id.cc_button)?.setOnClickListener { showSubtitleDialog() }

        // Set initial white tint before first onIsPlayingChanged fires
        playerView.findViewById<ImageButton>(MediaR.id.exo_play_pause)
            ?.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())

        loadMedia()
    }

    private fun loadMedia() {
        val baseUrl = serverPrefs.serverUrl
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.subtitleTracks(filePath)
                availableSubtitleTracks = response.tracks
            } catch (e: Exception) {
                availableSubtitleTracks = emptyList()
            }

            val subtitleConfigs = availableSubtitleTracks.map { track ->
                SubtitleConfiguration.Builder(Uri.parse(UrlBuilder.subtitleUrl(baseUrl, filePath, track.index)))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(track.language)
                    .setLabel(track.title)
                    .setSelectionFlags(if (track.index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }

            // Fall back to single subtitle if no tracks found (old behavior)
            val finalConfigs = if (subtitleConfigs.isEmpty()) {
                listOf(
                    SubtitleConfiguration.Builder(Uri.parse(UrlBuilder.subtitleUrl(baseUrl, filePath)))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            } else subtitleConfigs

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(UrlBuilder.streamUrl(baseUrl, filePath)))
                .setSubtitleConfigurations(finalConfigs)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    private fun showSubtitleDialog() {
        val options = mutableListOf("Off")
        if (availableSubtitleTracks.isEmpty()) {
            textTrackGroups.forEachIndexed { i, _ -> options.add("Subtitle ${i + 1}") }
        } else {
            availableSubtitleTracks.forEach { options.add(it.title) }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Subtitles")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    userDisabledSubtitles = true
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
                    )
                } else {
                    userDisabledSubtitles = false
                    val groupIndex = which - 1
                    val group = textTrackGroups.getOrNull(groupIndex) ?: return@setItems
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setDisabledTrackTypes(emptySet())
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(0)))
                    )
                }
                updateCcButtonState()
            }
            .show()
    }

    private fun updateCcButtonState() {
        val active = !userDisabledSubtitles && textTrackGroups.any { it.isSelected }
        val color = if (active) 0xFFE94560.toInt() else 0xFFFFFFFF.toInt()
        playerView.findViewById<Button>(R.id.cc_button)?.setTextColor(color)
    }

    private fun forceTextTrackIfNoneSelected(tracks: Tracks) {
        val anyTextSelected = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && group.isSelected
        }
        if (anyTextSelected) return

        val firstTextGroup = tracks.groups.firstOrNull { group ->
            group.type == C.TRACK_TYPE_TEXT &&
            group.length > 0 &&
            (0 until group.length).any { i -> group.isTrackSupported(i) }
        } ?: return

        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setOverrideForType(TrackSelectionOverride(firstTextGroup.mediaTrackGroup, emptyList()))
                .setSelectUndeterminedTextLanguage(true)
        )
    }

    fun isControllerVisible(): Boolean = playerView.isControllerFullyVisible

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

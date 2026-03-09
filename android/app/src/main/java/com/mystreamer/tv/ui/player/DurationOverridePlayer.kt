package com.mystreamer.tv.ui.player

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
class DurationOverridePlayer(
    player: Player,
    private val durationMs: Long
) : ForwardingPlayer(player) {
    override fun getDuration(): Long =
        if (super.getDuration() == C.TIME_UNSET && durationMs > 0) durationMs
        else super.getDuration()
}

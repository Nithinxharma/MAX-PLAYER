package xyz.mpv.rex

import android.content.Intent
import android.os.Bundle
import xyz.mpv.rex.ui.player.PlayerActivity

/**
 * RexPlayerActivity aliases PlayerActivity to provide an explicit, dedicated
 * entrypoint for CloudStream 3 playback handoff and external intent routing.
 */
class RexPlayerActivity : PlayerActivity()

/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class AutoHeightPlayerView constructor(context: Context, attrs: AttributeSet) :
    PlayerView(context, attrs),
    Player.Listener {
    private var currentPlayer: ExoPlayer? = null

    override fun setPlayer(player: Player?) {
        currentPlayer?.removeListener(this)
        super.setPlayer(player)
        currentPlayer = player as ExoPlayer?
        currentPlayer?.addListener(this)
    }

    override fun onVideoSizeChanged(size: VideoSize) {
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = currentPlayer?.videoFormat ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val measuredWidth = View.resolveSize(0, widthMeasureSpec)
        val measuredHeight = (measuredWidth.toDouble() * size.height / size.width).toInt()
        val newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.getMode(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }
}

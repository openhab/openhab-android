/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.media2.common.MediaItem
import androidx.media2.common.SessionPlayer
import androidx.media2.player.MediaPlayer
import androidx.media2.player.VideoSize
import androidx.media2.widget.VideoView
import java.util.concurrent.Executor

class AutoHeightVideoView constructor(context: Context, attrs: AttributeSet) : VideoView(context, attrs) {
    private var currentPlayer: SessionPlayer? = null
    private var videoSize: VideoSize? = null
    private val playerCallback = object : MediaPlayer.PlayerCallback() {
        override fun onVideoSizeChanged(mp: MediaPlayer, item: MediaItem, size: VideoSize) {
            super.onVideoSizeChanged(mp, item, size)
            if (size != videoSize) {
                videoSize = size
                requestLayout()
            }
        }
    }
    private val executor = object : Executor {
        private val handler = Handler(Looper.getMainLooper())
        override fun execute(r: Runnable?) {
            if (r != null) {
                handler.post(r)
            }
        }
    }

    override fun setPlayer(player: SessionPlayer) {
        currentPlayer?.unregisterPlayerCallback(playerCallback)
        super.setPlayer(player)
        player.registerPlayerCallback(executor, playerCallback)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = videoSize ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val measuredWidth = View.resolveSize(0, widthMeasureSpec)
        val measuredHeight = (measuredWidth.toDouble() * size.height / size.width).toInt()
        val newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.getMode(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }
}

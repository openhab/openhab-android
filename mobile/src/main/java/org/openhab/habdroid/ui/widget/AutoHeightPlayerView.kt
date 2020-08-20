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
import android.util.AttributeSet
import android.view.View
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.video.VideoListener

class AutoHeightPlayerView constructor(context: Context, attrs: AttributeSet) : PlayerView(context, attrs),
    VideoListener {
    private var currentPlayer: SimpleExoPlayer? = null

    override fun setPlayer(player: Player?) {
        currentPlayer?.removeVideoListener(this)
        super.setPlayer(player)
        currentPlayer = player as SimpleExoPlayer?
        currentPlayer?.addVideoListener(this)
    }

    override fun onVideoSizeChanged(
        width: Int,
        height: Int,
        unappliedRotationDegrees: Int,
        pixelWidthHeightRatio: Float
    ) {
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

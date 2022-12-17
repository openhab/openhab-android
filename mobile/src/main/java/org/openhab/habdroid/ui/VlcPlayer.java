/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

package org.openhab.habdroid.ui;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class VlcPlayer {

    private static final String TAG = VlcPlayer.class.getSimpleName();

    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;

    public void setup(Context context, VLCVideoLayout layout) {

        ArrayList<String> args = new ArrayList<>();

        args.add("--rtsp-tcp");
        args.add("--vout=android-display");
        args.add("-vvv");

        mLibVLC = new LibVLC(context, args);
        mMediaPlayer = new MediaPlayer(mLibVLC);

        mMediaPlayer.attachViews(
            layout,
            null,
            false,
            false
        );

        Log.d(TAG, "setup: media player setup complete");

    }

    public void setVideoUrl(String url) {

        Log.d(TAG, "setVideoUrl: url=" + url);

        Media media = new Media(mLibVLC, Uri.parse(url));

        media.setHWDecoderEnabled(true,true);

        mMediaPlayer.setMedia(media);

        media.release();
    }

    public void start() {

        Log.d(TAG, "start:");

        mMediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_5_4);

        mMediaPlayer.play();
    }

    public void stop() {

        mMediaPlayer.stop();

        IVLCVout vout = mMediaPlayer.getVLCVout();

        if (vout != null) {
            vout.detachViews();
        }

        mLibVLC.release();
        mLibVLC = null;
    }
}

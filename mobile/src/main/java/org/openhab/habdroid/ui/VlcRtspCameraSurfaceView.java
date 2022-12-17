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

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.videolan.libvlc.AWindow;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.util.ArrayList;

public class VlcRtspCameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
//public class VlcRtspCameraSurfaceView extends SurfaceView implements IVLCVout.Callback {

    private static final String TAG = VlcRtspCameraSurfaceView.class.getSimpleName();

    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;
    private String videoSource;
    private Context mContext;

    private View.OnLayoutChangeListener mOnLayoutChangeListener;

    public VlcRtspCameraSurfaceView(Context context) {
        super(context);

        setCustomSurfaceViewParams(context);
    }

    public VlcRtspCameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setCustomSurfaceViewParams(context);
    }

    public VlcRtspCameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setCustomSurfaceViewParams(context);
    }

    public VlcRtspCameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setCustomSurfaceViewParams(context);
    }

    private void setCustomSurfaceViewParams(Context context) {

        this.mContext = context;

//        getHolder().addCallback(this);

        Log.i(TAG, "setCustomSurfaceViewParams, view=" + hashCode());

        ((Activity) mContext).getWindow().setFormat(PixelFormat.UNKNOWN);

        getHolder().setFormat(PixelFormat.RGBA_8888);

        setFocusable(true);

//        mAWindow.addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {

        Log.d(TAG, "surfaceCreated:");
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        Log.d(TAG, "surfaceChanged:");
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

        Log.d(TAG, "surfaceDestroyed:");
    }

    public void setSource(@NotNull String videoSource) {
        this.videoSource = videoSource;

        Log.d(TAG, "setSource: source={}" + videoSource);
    }

    public void start() {

        Log.d(TAG, "start:");

        ArrayList<String> args = new ArrayList<>();
        args.add("--rtsp-tcp");
//        args.add("--vout=android-display");
        args.add("-vvv");

        mLibVLC = new LibVLC(mContext, args);
        mMediaPlayer = new MediaPlayer(mLibVLC);

        mMediaPlayer.getVLCVout().setSubtitlesView(this);
//        if (mSubtitlesSurface != null)
//            mAWindow.setSubtitlesView(mSubtitlesSurface);

        Log.d(TAG, "start: media player setup complete");

        Log.d(TAG, "start: starting playback of url [" + videoSource + "]");

        Media media = new Media(mLibVLC, Uri.parse(videoSource));

        media.setHWDecoderEnabled(true, true);

        mMediaPlayer.setMedia(media);

        media.release();

        mMediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_5_4);
        mMediaPlayer.play();
    }

    public void stop() {

        Log.d(TAG, "stop:");

        mMediaPlayer.stop();

//        IVLCVout vout = mMediaPlayer.getVLCVout();
//        vout.detachViews();

        mLibVLC.release();
        mLibVLC = null;

        mMediaPlayer = null;
    }

//    @Override
//    public void onSurfacesCreated(IVLCVout vlcVout) {
//
//    }
//
//    @Override
//    public void onSurfacesDestroyed(IVLCVout vlcVout) {
//
//    }
}

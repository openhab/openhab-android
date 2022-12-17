package org.openhab.habdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.openhab.habdroid.audio.AudioConnectionListener;
import org.openhab.habdroid.audio.AudioPoster;
import org.openhab.habdroid.audio.AudioQueue;
import org.openhab.habdroid.audio.AudioSampler;
import org.openhab.habdroid.audio.AudioSamplingListener;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

///////////////////////////////////////////////////////////////////
// NOTE THAT THE findViewById refers to a view defined in a fragment's layout xml file.
// That definition must now include a reference to RtspCameraSurfaceView.
//
// as in:
//          mySurfaceView = (RtspCameraSurfaceView) getActivity().findViewById(R.id.xxxxxxx);
//
///////////////////////////////////////////////////////////////////
//
// Methods implemented herein:
//
// check mjpegView.getmDisplayMode()
// check mjpegView.clearResources()
// check mjpegView.isPlaying()
// check mjpegView.isVideoPlaying()
// check mjpegView.stopPlayback()
// check videoView.setPlayer(videoPlayer)
// check videoView.setSource(videoUrl, VideoDoorBellFragment.this)
// check videoView.getSourceUrl()
// check mjpegView.start()
// check mjpegView.setDisplayMode(RtspCameraSurfaceView.SIZE_BEST_FIT); // SIZE_FULLSCREEN, SIZE_STANDARD, SIZE_VIDEO_DOORBELL
//
//
// Android SurfaceView methods which are overridden by RtspCameraSurfaceView
//
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
//    public void surfaceCreated(SurfaceHolder holder)
//    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
//    public void surfaceDestroyed(SurfaceHolder holder)
//
// The following are part of the android base SurfaceView class.
// The setOnClickListener is implemented in the application's activity or fragment.
// The rest are implemented in the base class only.
//
// mjpegView.setOnClickListener(this)
//
// mjpegView.getId()
// mjpegView.setVisibility( View.VISIBLE ) // also View.GONE
// mjpegView.setKeepScreenOn(true)
//
/////////////////////////////////////////////////////////////////////


public class RtspCameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = RtspCameraSurfaceView.class.getSimpleName();

    private Context mContext;
    public Surface mySurface;
    private SurfaceCreatedListener surfaceCreatedListener;
    private boolean surfaceDone = false;
    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;
    private String videoUrl;

    public RtspCameraSurfaceView(Context context) {
        super(context);
//        mDisplayMode = SIZE_STANDARD;
        Log.i("", "RtspCameraSurfaceView(context) ");
        setCustomSurfaceViewParams(context);
    }

    public RtspCameraSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Log.i("", "RtspCameraSurfaceView(context,attrs,defstyle) ");
//        mDisplayMode = SIZE_STANDARD;
        setCustomSurfaceViewParams(context);
    }

    public RtspCameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        String contx = context.getClass().toString();
        String attrx = attrs.toString();
        Log.i("", "RtspCameraSurfaceView( context= " + contx + " , attrs= " + attrx + " ) ");
//        mDisplayMode = SIZE_STANDARD;
        setCustomSurfaceViewParams(context);
    }

    public static String getUserPath(Context context) {
        PackageManager m = context.getPackageManager();
        String path = context.getPackageName();
        String userPath = "/data/data/" + path;
        Log.i("", "::getUserPath");
        try {
            PackageInfo p = m.getPackageInfo(path, 0);
            userPath = p.applicationInfo.dataDir;
        } catch (NameNotFoundException e) {
        }
        return userPath;
    }

    private void init(Context context) {

    }
    private void setCustomSurfaceViewParams(Context context) {

        this.mContext = context;

        getHolder().addCallback(this);

        Log.i("", "setCustomSurfaceViewParams, view=" + hashCode());

        ((Activity) mContext).getWindow().setFormat(PixelFormat.UNKNOWN);

        getHolder().setFormat(PixelFormat.RGBA_8888);

        setFocusable(true);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub

        mySurface = holder.getSurface();

        Log.i("", "surfaceCreated, view=" + hashCode() + ", surface=" + (mySurface == null ? "null" : mySurface.hashCode()));

        surfaceDone = true;
        if (surfaceCreatedListener != null) {
            surfaceCreatedListener.surfaceCreated();
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mySurface = holder.getSurface();

        Log.i("", "surfaceChanged, view=" + hashCode() + ", surface=" + (mySurface == null ? "null" : mySurface.hashCode()));
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDone = false;

        Log.i("", "surfaceDestroyed, view=" + hashCode());
    }

    public boolean isPlaying() {
        return false;
    }

    public void stopPlayback() {

        Log.d("", "stopPlayback, view=" + hashCode());

        mMediaPlayer.stop();

//        IVLCVout vout = mMediaPlayer.getVLCVout();
//        vout.detachViews();

        mLibVLC.release();
        mLibVLC = null;
    }

    //    public void setSource(String guid, String url, String audioUrl, Boolean keepPrecharged, Integer chargeTimeout, ICameraLiveFeedErrorListener cameraLiveFeedErrorListener) {
    public void setSource(String id, String url) {

        Log.i(TAG, "setSource id=" + id + ",url=" + url);

        videoUrl = url;
    }

    /**
     * @param retry true if is this a start from an error
     * @return status of player start, true is started
     */
    public boolean start(boolean retry) {

        Log.i(TAG, "start: view=" + hashCode() + ", retry=" + retry);

        ArrayList<String> args = new ArrayList<>();
        args.add("--rtsp-tcp");
        args.add("--vout=android-display");
        args.add("-vvv");

        mLibVLC = new LibVLC(mContext, args);
        mMediaPlayer = new MediaPlayer(mLibVLC);

        Log.d(TAG, "start: media player setup complete");

        Log.d(TAG, "start: starting playback of url [" + videoUrl + "]");

        Media media = new Media(mLibVLC, Uri.parse(videoUrl));

        media.setHWDecoderEnabled(true,true);

        mMediaPlayer.setMedia(media);

        media.release();

        mMediaPlayer.setVideoTrackEnabled(true);
        mMediaPlayer.play();

        return true;
    }

    /**
     * @param retry true if is this a start from an error
     * @return status of player start, true is started
     */
    public boolean restart(boolean retry) {
        Log.i("", "restart, view=" + hashCode());

        return false;
    }


    public void restoreVideo() {
        Log.i("", "restoreVideo ");
    }


    public void notifyErrorListener(final String playerId, final int contractId, boolean fullStop) {
        Log.i("", "notifyErrorListener, view=" + hashCode() + ", playerId=" + playerId + ", contractId" + contractId);
    }

    public boolean isVideoPlaying() {
        return false;
    }

    /**
     * Clearing the resources
     */
    public void clearResources() {
        Log.i(TAG, "RtspCameraSurfaceView - clear Resources, view=" + hashCode());

        stopPlayback();

        mContext = null;
        surfaceCreatedListener = null;
    }

    public void setSurfaceCreatedListener(SurfaceCreatedListener surfaceCreatedListener) {
        Log.i("", "setSurfaceCreatedListener: view=" + hashCode());
        this.surfaceCreatedListener = surfaceCreatedListener;

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int widthWithoutPadding = width - getPaddingLeft() - getPaddingRight();
        int heightWithoutPadding = height - getPaddingTop() - getPaddingBottom();
        int maxWidth;
        int maxHeight;

    }

    public interface SurfaceCreatedListener {
        void surfaceCreated();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    // Audio foo

    private Handler handler = new Handler();
    private boolean northboundStarted = false;
    private AudioManager audioManager;
    private boolean recordingInProgress;
    private ExecutorService executorService; // Using executor service to allow only 2 threads at most.
    private int voiceMode;
    private boolean wasSpeakerOn;
    private boolean listenInProgress;
    private boolean alreadyFetchedAudioUrl = false;

    private AudioSampler audioSampler; // North bound audio sampler that collects audio bytes from a recorder.
    private AudioQueue audioQueue;     // Audio buffer queue that is shared between AudioSampler and AudioPoster.
    private AudioPoster audioPoster;   // Audio poster that continually posts audio samples

    private void processCameraConnectionSuccess() {
        Log.d(TAG, "processCameraConnectionSuccess():");
        northboundStarted = true;
        toggleTalk();
//        talkLayout.setVisibility(View.VISIBLE);
    }

    private void processCameraConnectionFailure() {

        Log.d(TAG, "processCameraConnectionFailure()");

        northboundStarted = false;

        if (isPlaying() && isVideoPlaying()) {
// TODO
            //            MessageDialog.showDialog(getActivity(),
//                    getString(R.string.doorbell_answered),
//                    getString(R.string.doorbell_answered_desc));
        }

        stopAudioSampling();
        stopAudioStream();
//        updateAudioUIState(); TODO
        //talkLayout.setVisibility(View.INVISIBLE);
    }

    private void processConnectionBusy() {

        Log.d(TAG, "processConnectionBusy():");

        if (isPlaying() && isVideoPlaying()) {
            // TODO
//            MessageDialog.showDialog(getActivity(),
//                    getString(R.string.doorbell_answered),
//                    getString(R.string.doorbell_answered_desc));
        }
        stopAudioSampling();
        stopAudioStream();
//        updateAudioUIState();  TODO
    }

    private void processConnectionClosed() {

        Log.d(TAG, "processConnectionClosed()");

        stopAudioSampling();
        stopAudioStream();
//        updateAudioUIState();   TODO
        //talkLayout.setVisibility(View.INVISIBLE);
    }

    private void stopAudioSampling() {

        Log.i(TAG, "stopAudioSampling()");

        if (audioSampler != null) {
            // Stop sampling to mute the north bound audio streaming.
            audioSampler.stopSampling();
        }
        recordingInProgress = false;

        Log.i(TAG, "stopAudioSampling() - done");
    }

    private AudioSamplingListener audioSamplingListener = new AudioSamplingListener() {
        @Override
        public void onAudioSamplingError(Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "onAudioSamplingError()");
                    stopAudioSampling();
//                    updateAudioUIState();  TODO
                }
            });
        }
    };

    private void toggleTalk() {
//        talkLayout.setEnabled(true);
        if (recordingInProgress) {
            Log.d(TAG, "toggleTalk(): Stopping the sampling and posting threads.");

            stopAudioSampling();
        } else {
            try {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);


                // Start a AudioSampler to collect audio samples to post north bound.
                audioSampler = new AudioSampler(audioQueue);
                audioSampler.setAudioSamplingListener(audioSamplingListener);
                executorService.submit(audioSampler);

                Log.d(TAG, "toggleTalk(): Created new recording and posting threads.");

                recordingInProgress = true;
            } catch (IllegalStateException e) {
                Log.e(TAG, e.toString());
                stopAudioSampling();
                audioManager.setMode(voiceMode);

                //if speaker wasnt on, set it to false
                if (!wasSpeakerOn) {
                    audioManager.setSpeakerphoneOn(false);
                }
            }
//            dismissProgressDialog();   TODO
        }

        if (recordingInProgress && !listenInProgress) {
            listenInProgress = true;
//            m_cPlayer.unmute();
        }

//        updateAudioUIState();  TODO
    }

    // North bound audio state listener.
    private AudioConnectionListener northBoundAudioConnectionListener = new AudioConnectionListener() {
        @Override
        public void connectionSucceeded() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processCameraConnectionSuccess();
                }
            });
        }

        @Override
        public void connectionFailed() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processCameraConnectionFailure();
                }
            });
        }

        @Override
        public void connectionBusy() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processConnectionBusy();
                }
            });
        }

        @Override
        public void connectionClosed() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processConnectionClosed();
                }
            });
        }
    };

    private void startAudioStream(String audioUrl) {

        if (audioUrl == null) {
            Log.d(TAG, "startAudioStream: no audio url provided");

            return;
        }

        Log.d(TAG, "startAudioStream: audioUrl=" + audioUrl);

        audioQueue = new AudioQueue();  // create a queue to hold audio bytes that will be collected by an AudioSampler.

        audioPoster = new AudioPoster(
            audioQueue,
            audioUrl,
            northBoundAudioConnectionListener);

        Log.d(TAG, "startAudioStream: audioQueue=" + audioQueue + ", audioPoster=" + audioPoster);

        executorService.submit(audioPoster);
    }

    private void stopAudioStream() {

        Log.i(TAG, "stopAudioStream() ++");

        alreadyFetchedAudioUrl = false;

        northboundStarted = false;

        if (audioPoster != null) {
            audioPoster.stopSendingAudio();
            // Invalidate the audio session.
            // Currently we do not handle retry on failure.

            // TODO is there anything we need to do from an Android perspective here
        }

        Log.i(TAG, "stopAudioStream() --");
    }
}

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.media.AudioManager;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public final static int SIZE_STANDARD = 1;
    public final static int SIZE_BEST_FIT = 4;
    public final static int SIZE_FULLSCREEN = 8;
    public final static int SIZE_VIDEO_DOORBELL = 9;
    private final static float RATIO = 9.0f / 16.0f;
    private final static float CAMERA_RATIO = 16.0f / 9.0f;
    private final static int ALLOWED_STREAM_COUNT = 2; // North and South bound streams
    private static int iContractId = 0;

    static {
        System.loadLibrary("native-codec-jni");
        //RtspVideo.setMaxCameraLimit(1);
    }

    public Surface mySurface;
    public RtspVideo myRtspVideo = null;
    public String myLifePlayerInstanceId = null;
    public int m_nVideoWidth = 0;               // Video width
    public int m_nVideoHeight = 0;
    private int myContractId = -1;
    ////////////////////////////////////////////////////////////////////////////////
    private Context mContext;
    private RtspPlayer m_cPlayer = null;
    private boolean m_isResume = false;
    private boolean m_isPlayerRun = false;
    private boolean m_isVideoplaying = false;
    private boolean m_isPlayerStop = false;
    private int mDisplayMode = SIZE_STANDARD;
    private int mDisplayWidth = 640;
    private int mDisplayHeight = 480;
    //private VO_OSMP_ASPECT_RATIO m_nAspectRatio       = VO_OSMP_ASPECT_RATIO.VO_OSMP_RATIO_AUTO;
    private int m_nAspectRatio = 4; // 4 means 16:9
    private String m_strVideoPath = null;
    private Boolean m_restart = false;
    private String m_cameraGuid = null;
    private boolean m_isPlayerPrecharged = false;
    private boolean m_keepCharged = false;
    private int m_chargeTimeout = 0;
//    private ICameraLiveFeedErrorListener cameraLiveFeedErrorListener = null;
    private SurfaceCreatedListener surfaceCreatedListener;
    private boolean surfaceDone = false;
    private int defaultVolume;
    private String m_audioUrl;

    public RtspCameraSurfaceView(Context context) {
        super(context);
        mDisplayMode = SIZE_STANDARD;
        Log.i("", "RtspCameraSurfaceView(context) ");
        setCustomSurfaceViewParams(context);
    }

    public RtspCameraSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Log.i("", "RtspCameraSurfaceView(context,attrs,defstyle) ");
        mDisplayMode = SIZE_STANDARD;
        setCustomSurfaceViewParams(context);
    }

    public RtspCameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        String contx = context.getClass().toString();
        String attrx = attrs.toString();
        Log.i("", "RtspCameraSurfaceView( context= " + contx + " , attrs= " + attrx + " ) ");
        mDisplayMode = SIZE_STANDARD;
        setCustomSurfaceViewParams(context);
    }

    static void discharge() {
        RtspVideo.dischargeAllPlayers();
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

    private static int getUniqueContractId() {
        return iContractId++;
    }

    private void setPrecharged() {
        m_isPlayerPrecharged = true;
    }

    public boolean getPrecharged() {
        return m_isPlayerPrecharged;
    }

    public void clearPrecharged() {
        m_isPlayerPrecharged = false;
    }

    public void setPlayer(RtspPlayer player) {
        m_cPlayer = player;
        Log.i("", "setPlayer ");
    }

    private void setCustomSurfaceViewParams(Context context) {

        this.mContext = context;

        getHolder().addCallback(this);

        Log.i("", "setCustomSurfaceViewParams, view=" + hashCode());

        ((Activity) mContext).getWindow().setFormat(PixelFormat.UNKNOWN);

        getHolder().setFormat(PixelFormat.RGBA_8888);

        setFocusable(true);

        myRtspVideo = new RtspVideo(/*1, mContext,*/ null, this);

        executorService = Executors.newFixedThreadPool(ALLOWED_STREAM_COUNT);
    }

    public void setDisplayMode(int displayMode) {
        mDisplayMode = displayMode;
    }

    public int getmDisplayMode() {
        return mDisplayMode;
    }

    public int getmDisplayWidth() {
        return mDisplayWidth;
    }

    public int getmDisplayHeight() {
        return mDisplayHeight;
    }

    public void setDisaplayWidth(int width) {
        mDisplayWidth = width;
    }

    public void setDisplayHeight(int height) {
        mDisplayHeight = height;
    }


    // Display error messages and stop player
    public boolean onError(RtspPlayer mp, int what, int extra) {
        Log.i("", "onError, view=" + hashCode() + ",guid=" + m_cameraGuid);

        //Toast.makeText(mContext, (String)errStr, Toast.LENGTH_LONG).show();
        if (m_cPlayer != null) {
            m_cPlayer.closeStream();
            m_cPlayer.destroy();
            m_cPlayer = null;
            return true;
        }

        return false;
    }


    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub

        mySurface = holder.getSurface();

        Log.i("", "surfaceCreated, view=" + hashCode() + ", surface=" + (mySurface == null ? "null" : mySurface.hashCode()) + ",guid=" + m_cameraGuid);

        surfaceDone = true;
        if (surfaceCreatedListener != null) {
            surfaceCreatedListener.surfaceCreated();
        }

        myRtspVideo.setSurface(mySurface);

        if ((m_strVideoPath == null) || (m_strVideoPath.trim().length() <= 0)) {
            return;
        }

        if ((m_cameraGuid == null) || (m_cameraGuid.trim().length() <= 0)) {
            return;
        }

        if (m_cPlayer != null) {
            m_cPlayer.resume(this);
        }

        if (m_cPlayer == null) {
            m_cPlayer = new RtspPlayer(this, mContext);
        }
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mySurface = holder.getSurface();

        //if ( m_isPlayerRun == true ) {
        // stopPlayback();
        // playerStart();
        // }

        Log.i("", "surfaceChanged, view=" + hashCode() + ", surface=" + (mySurface == null ? "null" : mySurface.hashCode()));
        //if (m_cPlayer != null)
        //    m_cPlayer.setSurfaceChangeFinished();

        myRtspVideo.setSurface(mySurface);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDone = false;

        Log.i("", "surfaceDestroyed, view=" + hashCode() + ",guid=" + m_cameraGuid);

        if (m_cPlayer != null) {
            m_cPlayer.closeStream();
            //m_cPlayer.destroy();
            m_cPlayer = null;
        }

        if (myRtspVideo != null) {

            myRtspVideo.setSurface(null);

            if (myLifePlayerInstanceId != null) {
                myRtspVideo.destroyPlayer(myLifePlayerInstanceId);
                myLifePlayerInstanceId = null;
            }
        }
    }


    private void playerReset() {
        Log.i("", "playerReset, view=" + hashCode());

        m_nVideoWidth = 0;
        m_nVideoHeight = 0;
        m_nAspectRatio = 4;
    }


    public boolean isPlaying() {
        String logMsg = "isPlaying() returns " + m_isPlayerRun + ", view=" + hashCode();
        Log.i("", logMsg);

        return m_isPlayerRun;
    }

    public void stopPlayback() {
        Log.d("", "stopPlayback, view=" + hashCode() + ",guid=" + m_cameraGuid);

        myContractId = -1;

        if (m_isPlayerRun) {
            m_isPlayerStop = true;
            m_isPlayerRun = false;
            m_isVideoplaying = false;

            if (myLifePlayerInstanceId != null) {
                myRtspVideo.disconnect(myLifePlayerInstanceId);
                clearPrecharged();
            }

            if (m_cPlayer != null) {
                m_cPlayer.closeStream();
            }

            stopAudioStream();

            audioManager.setMode(voiceMode);

            //set stream volume to defalt
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0);

            //if speaker wasnt on to start, set it off on exit
            if (!wasSpeakerOn) {
                audioManager.setSpeakerphoneOn(false);
            }
        }
    }


    public void pausePlayback() {
        Log.d("", "pausePlayback, view=" + hashCode() + ",guid=" + m_cameraGuid);

        myContractId = -1;

        if (m_isPlayerRun) {
            m_isPlayerStop = true;
            m_isPlayerRun = false;
            m_isVideoplaying = false;

            if (myLifePlayerInstanceId != null) {
                myRtspVideo.pause(myLifePlayerInstanceId);
            }

            if (m_cPlayer != null) {
                m_cPlayer.closeStream();
            }
        }
    }


    public String getSourceUrl() {
        String logMsg = "getSourceUrl() returns " + m_strVideoPath + ", view=" + hashCode();
        Log.i("", logMsg);

        return m_strVideoPath;
    }

//    public void setSource(String guid, String url, String audioUrl, Boolean keepPrecharged, Integer chargeTimeout, ICameraLiveFeedErrorListener cameraLiveFeedErrorListener) {
        public void setSource(String guid, String url, String audioUrl, Boolean keepPrecharged, Integer chargeTimeout) {

        String logMsg = "setSource guid=" + guid + ", url=" + url+ ", audioUrl=" + audioUrl + ", view=" + hashCode();

        Log.i("", logMsg);

//        this.cameraLiveFeedErrorListener = cameraLiveFeedErrorListener;

        if (guid != null) {
            if (myRtspVideo == null) {
                myRtspVideo = new RtspVideo(/*1, mContext,*/ null, this);
            }

            if (myLifePlayerInstanceId != null) {
                // Enforce one player per view
                if (guid != null && !guid.equals(m_cameraGuid)) {
                    // Camera change - change player
                    myRtspVideo.destroyPlayer(myLifePlayerInstanceId);
                    myLifePlayerInstanceId = null;
                }
            }

            if ((url != null) && (chargeTimeout != null)) {
                m_chargeTimeout = chargeTimeout;
            }

            if (myLifePlayerInstanceId == null) {

                myLifePlayerInstanceId = myRtspVideo.createPlayer(guid, m_chargeTimeout);
                if (myLifePlayerInstanceId != null) {
                    Log.i("", "setSource created media player ");
                } else {
                    Log.i("", "setSource failed to create media player");
                }
            }

            m_cameraGuid = guid;
            m_strVideoPath = url;
            m_audioUrl = audioUrl;

            // direct URLs begin with "rtsp:" whereas indirect URLs begin with "rtsps:"
            Boolean isDirectUrl = false;
            if (url != null && url.regionMatches(true, 0, "rtsp:", 0, 5)) {
                isDirectUrl = true;
            }

            if ((url != null) && (keepPrecharged != null)) {
                m_keepCharged = keepPrecharged;
            }

            if (url != null && myLifePlayerInstanceId != null) {
                myRtspVideo.connect(myLifePlayerInstanceId, url);

                Log.d(TAG, "setSource url=" + url);

                setPrecharged();
            }
        }
    }

    /**
     * @param retry true if is this a start from an error
     * @return status of player start, true is started
     */
    public boolean start(boolean retry) {

        Log.i("", "start, view=" + hashCode() + ", guid=" + m_cameraGuid + ", retry=" + retry);

        audioManager = ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE));

        //get mode when first started
        voiceMode = audioManager.getMode();

        //get starting volume
        defaultVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        //get was speaker on
        wasSpeakerOn = audioManager.isSpeakerphoneOn();

        if (m_cameraGuid == null) {
            Log.i("", "start warning invalid (null) source guid, not starting, view=" + hashCode());
        } else if (myLifePlayerInstanceId == null) {
            Log.i("", "start warning invalid (null) player instance, not starting, view=" + hashCode());
        } else {
            myContractId = getUniqueContractId();
            m_restart = false;
            if (myRtspVideo.play(myLifePlayerInstanceId, myContractId)) {
                m_isPlayerRun = true;

                startAudioStream(m_audioUrl);

                return true;
            }
        }

//        if (cameraLiveFeedErrorListener != null) {
//            cameraLiveFeedErrorListener.onCameraLiveFeedError(retry);
//        }

        return false;
    }

    /**
     * @param retry true if is this a start from an error
     * @return status of player start, true is started
     */
    public boolean restart(boolean retry) {
        Log.i("", "restart, view=" + hashCode() + ", guid=" + m_cameraGuid);

        if (m_cameraGuid == null) {
            Log.i("", "restart warning invalid (null) source guid, not starting, view=" + hashCode());
        } else if (myLifePlayerInstanceId == null) {
            Log.i("", "restart warning invalid (null) player instance, not starting, view=" + hashCode());
        } else {
            myContractId = getUniqueContractId();
            m_restart = true;
            if (myRtspVideo.play(myLifePlayerInstanceId, myContractId)) {
                m_isPlayerRun = true;
                return true;
            }
        }

//        if (cameraLiveFeedErrorListener != null) {
//            cameraLiveFeedErrorListener.onCameraLiveFeedRestartError(retry);
//        }
        return false;
    }


    public void restoreVideo() {
        Log.i("", "restoreVideo ");
    }


    public void notifyErrorListener(final String playerId, final int contractId, boolean fullStop) {
        Log.i("", "notifyErrorListener, view=" + hashCode() + ", guid=" + m_cameraGuid + ", playerId=" + playerId + ", contractId" + contractId);

        final int thisContractId = myContractId;
        final String thisInstanceId = myLifePlayerInstanceId;

        stopPlayback();

//        if (cameraLiveFeedErrorListener != null && thisInstanceId != null &&
//                thisInstanceId.equals(playerId) && thisContractId == contractId) {
//            if (m_restart) {
//                cameraLiveFeedErrorListener.onCameraLiveFeedRestartError(fullStop);
//            } else {
//                cameraLiveFeedErrorListener.onCameraLiveFeedError(fullStop);
//            }
//        } else {
//            Log.i("", "notifyErrorListener id mismatch, thisId=" + thisInstanceId + ", thisContract=" + thisContractId);
//        }
    }

    public void notifyVideoPlaying(final String playerId, final int contractId, boolean isVideoPlaying) {
        String logMsg = "notifyVideoPlaying(" + isVideoPlaying + "), view=" + hashCode() + ",guid=" + m_cameraGuid;
        Log.i("", logMsg);

//        if (cameraLiveFeedErrorListener != null && myLifePlayerInstanceId != null &&
//                myLifePlayerInstanceId.equals(playerId) && myContractId == contractId) {
//            m_isVideoplaying = isVideoPlaying;
//            cameraLiveFeedErrorListener.onVideoPlaying();
//        }
    }

    public boolean isVideoPlaying() {
        String logMsg = "isVideoPlaying: returns " + m_isVideoplaying + ", view=" + hashCode();
        Log.i("", logMsg);
        return m_isVideoplaying;
    }

    /**
     * Clearing the resources
     */
    public void clearResources() {
        Log.i(TAG, "RtspCameraSurfaceView - clear Resources, view=" + hashCode());
        //If mPlayer is playing stop the playback
        //if (m_cPlayer != null && m_isPlayerRun) {
        //if ( myRtspVideo != null) {
        //	myRtspVideo.doJavaDebug("clearResources");
        //}
        stopPlayback();
        //}
        if (myLifePlayerInstanceId != null) {
            myRtspVideo.destroyPlayer(myLifePlayerInstanceId);
            myLifePlayerInstanceId = null;
        }

        m_cPlayer = null;
        mContext = null;
        surfaceCreatedListener = null;
//        cameraLiveFeedErrorListener = null;
        myRtspVideo = null;
//		mIn = null;
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
        switch (mDisplayMode) {

            case SIZE_VIDEO_DOORBELL:

                //Corrections to 9/16 Mode
                //maxWidth = (int) (heightWithoutPadding * RATIO);
                maxHeight = heightWithoutPadding;//(int) (widthWithoutPadding / RATIO);
                maxWidth = (int) (heightWithoutPadding * RATIO);

                //if (widthWithoutPadding < maxWidth) {
                width = maxWidth + getPaddingLeft() + getPaddingRight();
                //}else{
                //width = widthWithoutPadding;
                //}

                if (heightWithoutPadding < maxHeight) {
                    height = maxHeight + getPaddingTop() + getPaddingBottom();
                } else {
                    height = heightWithoutPadding;
                }
                break;

            case SIZE_FULLSCREEN:
            case SIZE_STANDARD:
            case SIZE_BEST_FIT:
                maxWidth = (int) (heightWithoutPadding * CAMERA_RATIO);
                maxHeight = (int) (widthWithoutPadding / CAMERA_RATIO);

                if (widthWithoutPadding < maxWidth) {
                    width = maxWidth + getPaddingLeft() + getPaddingRight();
                } else {
                    width = widthWithoutPadding;
                }

                if (heightWithoutPadding < maxHeight) {
                    height = maxHeight + getPaddingTop() + getPaddingBottom();
                } else {
                    height = heightWithoutPadding;
                }
                break;

            default:
                break;
        }

        //String logmsg = "onMeasure( " + width + " , " + height + " )";
        //Log.i("", logmsg);

        setMeasuredDimension(width, height);
        setDisaplayWidth(width);
        setDisplayHeight(height);

    } // onMeasure

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
            m_cPlayer.unmute();
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

//                    if (isAdded()) {
//                        talkLayout.setEnabled(true);
//                        // North bound audio stream is successfully established. Update the UI.
//                        processCameraConnectionSuccess();
//                    }
                }
            });
        }

        @Override
        public void connectionFailed() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processCameraConnectionFailure();

//                    if (isAdded()) {
//                        talkLayout.setEnabled(true);
//                        // An error occurred while connecting north bound audio stream. Update the UI.
//                        processCameraConnectionFailure();
//                    }
                }
            });
        }

        @Override
        public void connectionBusy() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processConnectionBusy();

//                    if (isAdded()) {
//                        talkLayout.setEnabled(true);
//                        // Connection was rejected because VDB is currently busy.
//                        processConnectionBusy();
//                    }
                }
            });
        }

        @Override
        public void connectionClosed() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    processConnectionClosed();

//                    if (isAdded()) {
//                        talkLayout.setEnabled(true);
//                        // North bound audio stream has been closed. Update the UI.
//                        processConnectionClosed();
//                    }
                }
            });
        }
    };

    private void startAudioStream(String audioUrl) {

        if( audioUrl == null ) {
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

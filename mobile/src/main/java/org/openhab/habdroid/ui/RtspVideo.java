package org.openhab.habdroid.ui;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;


public class RtspVideo {
    private static final String TAG = RtspVideo.class.getSimpleName();

    //-----------------------------------------------------------------------------------
    // RtspVideo class
    //
    // Implements JNI interface to rtsp_player library.
    //
    // Multiple instances of RtspVideo is supported, if each instance is
    // connected to a different camera guid.  Multiple instances to the same camera is allowed,
    // as long as a different camera guid is assigned to each instance.
    //
    // Multiview (more than one view to the same camera guid) is supported but not recommended.
    // The rtsp_player library enforces one player per camera guid, and will attempt to
    // respond to commands from multiple RtspVideo instances, but the outcome is not
    // guaranteed (only one view will get the live output pictures).
    //
    // Multiple players for the same view: this is not supported.
    //------------------------------------------------------------------------------------

    private static final String MIME_TYPE = "video/avc";
    //public static Context dumCtx;
    Surface m_surface;
    RtspCameraSurfaceView m_view;

    //---------------------------------------------------------------------------------------------
    //  RtspVideo constructor
    //
    //  Create and instance, associated with a view and surface
    //  The surface can be changed any time (latched, effective on play)
    //  The view cannot be changed.
    //---------------------------------------------------------------------------------------------
    public RtspVideo(/*int pValue, Context context,*/ Surface surface, RtspCameraSurfaceView view) {

        Log.d(TAG, "RtspVideo::constructor, obj=" + hashCode());
        //dumCtx = context;
        m_surface = surface;
        m_view = view;

        AccessStaticMethods(this);

        //setMaxCameraLimit(-1);
    }

    // Static API functions
    private static native void AccessStaticMethods(RtspVideo lifeVid);

    private static native void javadebug(String debugstr);

    private static native void dischargeStreamingMediaPlayers();

    private static native void setMaxStreamingMediaDecoders(int limit);

    public static void toast(String message) {
        //String msg = message;
        //Toast.makeText( dumCtx, msg , Toast.LENGTH_SHORT).show();
    }

    public static void ExceptionListener(final String exception) {
        Log.e(TAG,"RtspVideo::ExceptionListener, " + exception, null);
    }

    //---------------------------------------------------------------------------------------------
    //  dischargeAllPlayers()
    //
    //  All players are disconnected from the network.
    //---------------------------------------------------------------------------------------------
    public static void dischargeAllPlayers() {
        Log.d(TAG,"RtspVideo::doDischarge");
        dischargeStreamingMediaPlayers();
    }

    //---------------------------------------------------------------------------------------------
    //  setMaxCameraLimit()
    //
    //  Set maximum number of camera guids that the player library will limit to.
    //  Optional.
    //  Setting limit to a negative value  means no limit, this is the default
    //
    //  Once the camera guid limit is reached, createPlayer will return null for new camera guids.
    //  If all player instances for a camera guid are destroyed a new camera guid slot will be available.
    //
    //  Note, the library will allow more than one player instance per camera guid, but it will only actively
    //  play one at a time.
    //---------------------------------------------------------------------------------------------
    public static void setMaxCameraLimit(int limit) {
        Log.d(TAG,"RtspVideo::setMaxCameraLimit");
        setMaxStreamingMediaDecoders(limit);
    }

    /////////////////////////////////////////////

    // Instance API functions
    private native String createStreamingMediaPlayer(String guid, int chargeTimeout);

    private native void connectStreamingMediaPlayer(String player_id, String url);

    private native boolean startPlayingStreamingMediaPlayer(String player_id, int contract_id, int timeout);

    private native void pausePlayingStreamingMediaPlayer(String player_id);

    //***************************************************************************
    //
    //  Status Listener callbacks from Player
    //
    //---------------------------------------------------------------------------

    private native void disconnectStreamingMediaPlayer(String player_id);

    private native void destroyStreamingMediaPlayer(String player_id);

    //***************************************************************************
    //
    //  End of Status Listener callbacks from Player
    //
    //---------------------------------------------------------------------------

    private native void muteStreamingMediaPlayer(String player_id);

    private native void unmuteStreamingMediaPlayer(String player_id);

    public void StatusListener(final String playerId, final int contractId, final int status) {
        // status values
        // 0    success
        // 1    cannotStart
        // 2    streamLimit
        // 3    timeout
        // 4    networkDown
        // 5    networkUp

        Log.d(TAG,"RtspVideo::StatusListener, obj=" + hashCode() + ", id=" + playerId + ", contract=" + contractId + ", status=" + status);

        switch (status) {
            case 0:
                m_view.getHandler().post(new Runnable() {
                    public void run() {
                        m_view.notifyVideoPlaying(playerId, contractId, true);
                    }
                });
                break;

            case 1:
            case 2:
            case 3:
                m_view.getHandler().post(new Runnable() {
                    public void run() {
                        m_view.notifyErrorListener(playerId, contractId, false);
                    }
                });
                break;

            case 4:
            case 5:
                // Give user an indication that a playing stream is losing and regaining connectivity, after good stream start
                break;

            default:
                break;
        }
    }

    //-----------------------------------------------------------------------------------
    // CreateOwnCodec(format)
    //
    // Callback from rtsp_player to create a MediaCodec decoder
    // Native MediaCodec not yet available on Android 4.1
    //------------------------------------------------------------------------------------
    public MediaCodec CreateOwnCodec(MediaFormat format) {
        MediaCodec decoder = null;

        Log.d(TAG,"RtspVideo static CreateOwnCodec, obj=" + hashCode() + ", surface=" + (m_surface == null ? "null" : m_surface.hashCode()));

        if (m_surface != null) {
            //Surface xSurface = dumSurface;
            Surface xSurface = m_surface;

            try {
                decoder = MediaCodec.createDecoderByType(MIME_TYPE); // this alone works
                decoder.configure(format, xSurface, null, 0);
            } catch (IOException e) {
                decoder = null;
            }
        }

        return decoder;
    }

    //-----------------------------------------------------------------------------------
    //  setSurface(surface)
    //
    //  rtsp_player will connect decoder to surface on play.
    //  It will use latest surface latched by this method.
    //
    //  This does not change surfaces while playing.
    //  Call this method before play.
    //
    //  if surface is null, player will not output pictures to any surface (on play)
    //-----------------------------------------------------------------------------------
    public void setSurface(Surface surface) {
        Log.d(TAG,"RtspVideo setSurface, obj=" + hashCode() + ", surface=" + (surface == null ? "null" : surface.hashCode()));

        m_surface = surface;
    }

    //---------------------------------------------------------------------------------------------
    //  playerId createPlayer(guid)
    //
    //  Create a player instance.  Camera Guid parameter is required.
    //
    //  Application must call this before calling other player methods, e.g. play, stop, destroy.
    //
    //  Returns playerId, which must be used with subsequent player methods.
    //---------------------------------------------------------------------------------------------
    public String createPlayer(String guid, int chargeTimeout) {
        Log.d(TAG,"RtspVideo::createPlayer, obj=" + hashCode());
        return createStreamingMediaPlayer(guid, chargeTimeout);
    }

    //---------------------------------------------------------------------------------------------
    //  connect(playerId, url)
    //
    //  Connect a player to a media URL.
    //
    //  Application should call this if the player is not already connected to the associated camera.
    //---------------------------------------------------------------------------------------------
    public void connect(String playerId, String url) {
        Log.d(TAG,"RtspVideo::connect, obj=" + hashCode() + ", id=" + playerId);
        if (playerId != null && url != null) {
            connectStreamingMediaPlayer(playerId, url);
        }
    }

    //---------------------------------------------------------------------------------------------
    //  play(playerId, contractId)
    //
    //  playerId: player Id
    //  contractId:
    //
    //  Playback begins
    //
    //  A feedback contract is created:
    //    Player will call back with either "cannot start" or "playing"
    //    In the callback it will report both playerId and contractId
    //---------------------------------------------------------------------------------------------
    public boolean play(String playerId, int contractId) {
        boolean success = false;
        int timeout = 10;   // sec

        Log.d(TAG,"RtspVideo::play, obj=" + hashCode() + ", id=" + playerId + ", contract=" + contractId);
        if (playerId != null) {
            success = startPlayingStreamingMediaPlayer(playerId, contractId, timeout);
        }

        return success;
    }

    //---------------------------------------------------------------------------------------------
    //  pause(playerId)
    //
    //  Pause playback
    //---------------------------------------------------------------------------------------------
    public void pause(String playerId) {
        Log.d(TAG,"RtspVideo::pause, obj=" + hashCode() + ", id=" + playerId);
        if (playerId != null) {
            pausePlayingStreamingMediaPlayer(playerId);
        }
    }

    //---------------------------------------------------------------------------------------------
    //  disconnect(playerId)
    //
    //  Disconnect the player from the network.
    //  Do not call this to implement a precharged model, otherwise it will take a long time
    //  to get new URLs and reconnect the camera.  All cameras are disconnected with discharge().
    //---------------------------------------------------------------------------------------------
    public void disconnect(String playerId) {
        Log.d(TAG,"RtspVideo::disconnect, obj=" + hashCode() + ", id=" + playerId);
        if (playerId != null) {
            disconnectStreamingMediaPlayer(playerId);
        }
    }

    //---------------------------------------------------------------------------------------------
    //  destroy(playerId)
    //
    //  Remove thus player instance.  If this player is actively playing to a surface, it is stopped.
    //  rtsp_player will not disconnect from the network, it will only pause and keep the connection alive,
    //  until discharge is called.
    //---------------------------------------------------------------------------------------------
    public void destroyPlayer(String playerId) {
        Log.d(TAG,"RtspVideo::destroyPlayer, obj=" + hashCode() + ", id=" + playerId);
        if (playerId != null) {
            destroyStreamingMediaPlayer(playerId);
        }
    }

    //---------------------------------------------------------------------------------------------
    //  mute()
    //
    //  Mute audio for the given player
    //---------------------------------------------------------------------------------------------
    public void mute(String playerId) {
        Log.d(TAG,"RtspVideo::mute");
        if (playerId != null) {
            muteStreamingMediaPlayer(playerId);
        }
    }

    //---------------------------------------------------------------------------------------------
    //  unmute()
    //
    //  Unmute audio for the given player
    //---------------------------------------------------------------------------------------------
    public void unmute(String playerId) {
        Log.d(TAG,"RtspVideo::unmute");
        if (playerId != null) {
            unmuteStreamingMediaPlayer(playerId);
        }
    }

    public void doJavaDebug(String dbgstr) {
        javadebug(dbgstr);
    }

}  // RtspVideo class


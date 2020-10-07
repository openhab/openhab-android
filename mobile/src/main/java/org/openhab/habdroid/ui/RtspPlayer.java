package org.openhab.habdroid.ui;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;

//videoPlayer.closeStream()
//videoPlayer.destroy()


public class RtspPlayer {
    private static final String TAG = RtspPlayer.class.getSimpleName();

    private RtspPlayer m_cPlayer = null;
    private RtspCameraSurfaceView m_sview = null;
    private Context mContext = null;
    private boolean isMute;

    public RtspPlayer(RtspCameraSurfaceView sv_view, Context ctx) {
        this.m_sview = sv_view;
        this.mContext = ctx;
        InitView();
    }

    public RtspPlayer getPlayer() {
        return m_cPlayer;
    }

    private void setPlayer(RtspPlayer player) {
        m_cPlayer = player;
    }

    private void setPlayerSize(int bmw, int bmh) {

        // fix for white bottom bar on Visual On
        //if (m_sview.getmDisplayMode() == CustomDLSurfaceView.SIZE_BEST_FIT)
        //    m_cPlayer.setZoomMode(VOOSMPType.VO_OSMP_ZOOM_MODE.VO_OSMP_ZOOM_FITWINDOW, null);
        //}

        //if (m_sview.getmDisplayMode() == 0) {
        //    m_cPlayer.setViewSize(bmw, bmh);
        //}

        //if(m_sview.getmDisplayWidth() !=0 && m_sview.getmDisplayHeight() !=0){
        //    m_cPlayer.setViewSize(m_sview.getmDisplayWidth(),m_sview.getmDisplayHeight());
        //}
    }

    /**
     * Mute Doorbell inbound stream
     */
    public void mute() {
        if (m_sview != null) {
            m_sview.myRtspVideo.mute(m_sview.myLifePlayerInstanceId);
        }
        isMute = true;
    }

    public boolean isMute() {
        return isMute;
    }

    /**
     * Unbumte doorbell inbound stream
     */
    public void unmute() {
        if (m_sview != null && m_sview.myRtspVideo != null) {
            m_sview.myRtspVideo.unmute(m_sview.myLifePlayerInstanceId);
            isMute = false;
        }
    }

    private void InitView() {
        if (m_cPlayer == null) {

            // Initialize the SDK
            int nRet;

            //m_cPlayer = new RtspPlayer();
            m_cPlayer = this;

            setPlayer(m_cPlayer);

            m_sview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_NORMAL);

            // Retrieve location of libraries
            String apkPath = m_sview.getUserPath(mContext) + "/lib/";

            String cfgPath = m_sview.getUserPath(mContext) + "/";

            //init.setContext(mContext);

            // Set view
            //m_cPlayer.setView(m_sview);

            // Set surface view size
            DisplayMetrics dm = new DisplayMetrics();
            ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(dm);

            setPlayerSize(dm.widthPixels, dm.heightPixels);

            // Register SDK event listener
            //m_cPlayer.setOnEventListener(m_listenerEvent);

            // Set device capability file location
            String capFile = cfgPath + "cap.xml";
            //m_cPlayer.setDeviceCapabilityByFile(capFile);

        }
    }


    public void openPLayer() {
        //if(m_cPlayer!=null){
        // }
    }


    /*
     private VOCommonPlayerListener m_listenerEvent = new VOCommonPlayerListener() {
    };
    */

    public int clearSelection() {

        return 0; // m_cPlayer.clearSelection();  // OOPS RECURSION NOW
    }


    public void closeStream() {
        if (m_cPlayer != null) {
            //m_cPlayer.stop();
            //m_cPlayer.close();
        }
    }

    public void destroy() {
        if (m_cPlayer != null) {
            //m_cPlayer.destroy(); // OOPS RECURSION NOW
        }
    }

    public void resume(RtspCameraSurfaceView s_view) {
        // m_cPlayer.resume(s_view); // OOPS RECURSION NOW
    }


    public int pause() {
        int nret = 0; //m_cPlayer.pause(); // OOPS RECURSION NOW
        return nret;
    }

    public void setSurfaceChangeFinished() {
        // m_cPlayer.setSurfaceChangeFinished(); // OOPS RECURSION NOW
    }
}

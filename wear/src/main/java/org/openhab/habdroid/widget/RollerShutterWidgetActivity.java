package org.openhab.habdroid.widget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;

import org.openhab.habdroid.R;
import org.openhab.habdroid.service.MobileService;
import org.openhab.habdroid.service.MobileServiceWdigetClient;
import org.openhab.habdroid.view.ProgressWheel;
import org.openhab.habdroid.view.ShutterCancleCircleView;
import org.openhab.habdroid.view.ShutterDownTriangleView;
import org.openhab.habdroid.view.ShutterUpTriangleView;

/**
 * Created by tobiasamon on 22.03.15.
 */
public class RollerShutterWidgetActivity extends Activity implements MobileServiceWdigetClient {

    public static final String WIDGET_LINK = "widget_link";
    public static final String WIDGET_NAME = "widget_name";
    public static final String ITEM_NAME = "item_name";
    private static final String TAG = RollerShutterWidgetActivity.class.getSimpleName();

    private String mWidgetLabel;

    private String mWidgetLink;

    private String mItemName;

    private ShutterCancleCircleView mCancelView;

    private ShutterUpTriangleView mUpView;

    private ShutterDownTriangleView mDownView;

    private ProgressWheel mProgressWheel;

    private MobileService mMobileService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shutter_widget_layout);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub_switch);

        mWidgetLink = getIntent().getStringExtra(WIDGET_LINK);

        mWidgetLabel = getIntent().getStringExtra(WIDGET_NAME);

        mItemName = getIntent().getStringExtra(ITEM_NAME);

        mMobileService = MobileService.getService(getApplicationContext());
        mMobileService.addClient(this);

        Log.d(TAG, "Current widget link " + mWidgetLink);
        Log.d(TAG, "Widget item name " + mItemName);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mProgressWheel = (ProgressWheel) findViewById(R.id.progressBar);

                mCancelView = (ShutterCancleCircleView) stub.findViewById(R.id.shutterStop);
                mCancelView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mProgressWheel.spin();
                        String command = "/CMD?" + mItemName + "=STOP";
                        mMobileService.sendCommand(command, mWidgetLink);
                    }
                });
                mUpView = (ShutterUpTriangleView) stub.findViewById(R.id.shutterUp);
                mUpView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mProgressWheel.spin();
                        String command = "/CMD?" + mItemName + "=UP";
                        mMobileService.sendCommand(command, mWidgetLink);
                    }
                });
                mDownView = (ShutterDownTriangleView) stub.findViewById(R.id.shutterDown);
                mDownView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mProgressWheel.spin();
                        String command = "/CMD?" + mItemName + "=DOWN";
                        mMobileService.sendCommand(command, mWidgetLink);
                    }
                });
            }
        });
    }

    @Override
    public void commandExecuted(boolean success) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mProgressWheel.stopSpinning();
            }
        });
    }

    @Override
    public void connected() {

    }

    @Override
    public void connectionSuspended() {

    }
}

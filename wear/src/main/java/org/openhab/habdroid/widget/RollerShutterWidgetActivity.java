package org.openhab.habdroid.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.activity.ConfirmationActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;

import org.openhab.habdroid.R;
import org.openhab.habdroid.service.GoogleApiService;
import org.openhab.habdroid.service.SendCommandAsync;
import org.openhab.habdroid.util.SharedConstants;
import org.openhab.habdroid.view.ProgressWheel;
import org.openhab.habdroid.view.ShutterCancleCircleView;
import org.openhab.habdroid.view.ShutterDownTriangleView;
import org.openhab.habdroid.view.ShutterUpTriangleView;

/**
 * Created by tobiasamon on 22.03.15.
 */
public class RollerShutterWidgetActivity extends Activity implements MessageApi.MessageListener {

    public static final String WIDGET_LINK = "widget_link";
    public static final String WIDGET_NAME = "widget_name";
    public static final String ITEM_NAME = "item_name";
    private static final String TAG = RollerShutterWidgetActivity.class.getSimpleName();

    private String mWidgetLabel;

    private String mWidgetLink;

    private String mItemName;

    private GoogleApiService mGoogleApiService;

    private ShutterCancleCircleView mCancelView;

    private ShutterUpTriangleView mUpView;

    private ShutterDownTriangleView mDownView;

    private ProgressWheel mProgressWheel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shutter_widget_layout);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub_switch);

        mWidgetLink = getIntent().getStringExtra(WIDGET_LINK);

        mWidgetLabel = getIntent().getStringExtra(WIDGET_NAME);

        mItemName = getIntent().getStringExtra(ITEM_NAME);

        mGoogleApiService = new GoogleApiService(getApplicationContext());
        mGoogleApiService.connect();

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
                        new SendCommandAsync(getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, command, mWidgetLink);
                        mGoogleApiService.addMessageListener(RollerShutterWidgetActivity.this);
                    }
                });
                mUpView = (ShutterUpTriangleView) stub.findViewById(R.id.shutterUp);
                mUpView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mProgressWheel.spin();
                        String command = "/CMD?" + mItemName + "=UP";
                        new SendCommandAsync(getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, command, mWidgetLink);
                        mGoogleApiService.addMessageListener(RollerShutterWidgetActivity.this);
                    }
                });
                mDownView = (ShutterDownTriangleView) stub.findViewById(R.id.shutterDown);
                mDownView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mProgressWheel.spin();
                        String command = "/CMD?" + mItemName + "=DOWN";
                        new SendCommandAsync(getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, command, mWidgetLink);
                        mGoogleApiService.addMessageListener(RollerShutterWidgetActivity.this);
                    }
                });
            }
        });
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Received a message at path " + messageEvent.getPath());
        if (messageEvent.getPath().endsWith(SharedConstants.MessagePath.SUCCESS.value())) {
            final String result = new String(messageEvent.getData());
            mGoogleApiService.removeMessageListener(RollerShutterWidgetActivity.this);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mProgressWheel.stopSpinning();
                }
            });
        }
    }
}

package org.openhab.habdroid.widget;

import android.app.Activity;
import android.content.Intent;
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
import org.openhab.habdroid.util.SharedConstants;

/**
 * Created by tobiasamon on 22.03.15.
 */
public class RollerShutterWidgetActivity extends Activity implements MessageApi.MessageListener {

    public static final String STATE = "state";
    public static final String WIDGET_LINK = "widget_link";
    public static final String WIDGET_NAME = "widget_name";
    private static final String TAG = RollerShutterWidgetActivity.class.getSimpleName();
    private boolean mCurrentState;

    private String mWidgetLabel;

    private String mWidgetLink;

    private TextView mSwitchName;

    private ProgressBar mProgressBar;

    private Switch mSwitch;

    private GoogleApiService mGoogleApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shutter_widget_layout);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub_switch);

        mCurrentState = getIntent().getBooleanExtra(STATE, false); // default state -> false

        mWidgetLink = getIntent().getStringExtra(WIDGET_LINK);

        mWidgetLabel = getIntent().getStringExtra(WIDGET_NAME);


        mGoogleApiService = new GoogleApiService(getApplicationContext());
        mGoogleApiService.connect();

        Log.d(TAG, "Current widget link " + mWidgetLink);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
            }
        });
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Received a message at path " + messageEvent.getPath());
        if (messageEvent.getPath().endsWith(SharedConstants.MessagePath.SUCCESS.value())) {
            final String result = new String(messageEvent.getData());
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (result.equals(mWidgetLink)) {
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mCurrentState = !mCurrentState;
                        Intent intent = new Intent(RollerShutterWidgetActivity.this, ConfirmationActivity.class);
                        intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                                ConfirmationActivity.SUCCESS_ANIMATION);
                        intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, "");
                        startActivity(intent);
                    }
                }
            });
        }
    }
}

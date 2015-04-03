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
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.service.MobileService;
import org.openhab.habdroid.service.MobileServiceWdigetClient;
import org.openhab.habdroid.view.ProgressWheel;
import org.openhab.habdroid.view.SwitchCircleView;

/**
 * Created by tobiasamon on 22.03.15.
 */
public class SwitchWidgetActivity extends Activity implements MobileServiceWdigetClient {

    public static final String STATE = "state";
    public static final String WIDGET_LINK = "widget_link";
    public static final String WIDGET_NAME = "widget_name";
    private static final String TAG = SwitchWidgetActivity.class.getSimpleName();
    private boolean mCurrentState;

    private String mWidgetLabel;

    private String mWidgetLink;

    private TextView mSwitchName;

    private ProgressWheel mProgressBar;

    private SwitchCircleView mSwitch;

    private MobileService mMobileService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.switch_widget_layout);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub_switch);

        mCurrentState = getIntent().getBooleanExtra(STATE, false); // default state -> false

        mWidgetLink = getIntent().getStringExtra(WIDGET_LINK);

        mWidgetLabel = getIntent().getStringExtra(WIDGET_NAME);

        mMobileService = MobileService.getService(getApplicationContext());
        mMobileService.addClient(this);

        Log.d(TAG, "Current widget link " + mWidgetLink);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mSwitchName = (TextView) findViewById(R.id.switchName);
                mProgressBar = (ProgressWheel) findViewById(R.id.progressBar);
                mSwitch = (SwitchCircleView) findViewById(R.id.switchCircle);

                mSwitchName.setText(mWidgetLabel);

                mSwitch.setCurrentState(mCurrentState);

                mSwitch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String command = mCurrentState ? "OFF" : "ON";
                        mSwitch.setCurrentState(!mCurrentState);
                        mProgressBar.setVisibility(View.VISIBLE);
                        mProgressBar.spin();
                        mMobileService.sendCommand(command, mWidgetLink);
                    }
                });
            }
        });
    }

    @Override
    public void commandExecuted(boolean success) {

        if (success) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.stopSpinning();
                    mCurrentState = !mCurrentState;
                    if (mCurrentState) {
                        mSwitch.setText("On");
                    } else {
                        mSwitch.setText("Off");
                    }
                    Intent intent = new Intent(SwitchWidgetActivity.this, ConfirmationActivity.class);
                    intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                            ConfirmationActivity.SUCCESS_ANIMATION);
                    intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, "");
                    startActivity(intent);
                }

            });
        }
    }

    @Override
    public void connected() {
        // ??
    }

    @Override
    public void connectionSuspended() {
        // ??
    }
}

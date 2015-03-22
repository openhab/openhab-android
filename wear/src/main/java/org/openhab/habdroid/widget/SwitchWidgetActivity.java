package org.openhab.habdroid.widget;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;

import org.openhab.habdroid.R;
import org.openhab.habdroid.service.GoogleApiService;
import org.openhab.habdroid.service.SendCommandAsync;
import org.openhab.habdroid.util.SharedConstants;

/**
 * Created by tobiasamon on 22.03.15.
 */
public class SwitchWidgetActivity extends Activity implements MessageApi.MessageListener {

    public static final String STATE = "state";
    public static final String WIDGET_LINK = "widget_link";
    private static final String TAG = SwitchWidgetActivity.class.getSimpleName();
    private boolean mCurrentState;

    private String mWidgetLink;

    private TextView mStatusTextView;

    private GoogleApiService mGoogleApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.switch_widget_layout);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub_switch);

        mCurrentState = getIntent().getBooleanExtra(STATE, false); // default state -> false

        mWidgetLink = getIntent().getStringExtra(WIDGET_LINK);

        mGoogleApiService = new GoogleApiService(getApplicationContext());
        mGoogleApiService.connect();

        Log.d(TAG, "Current widget link " + mWidgetLink);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mStatusTextView = (TextView) findViewById(R.id.switch_status);

                if (mCurrentState) {
                    mStatusTextView.setText("ON");
                } else {
                    mStatusTextView.setText("OFF");
                }

                mStatusTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String command = mCurrentState ? "OFF" : "ON";
                        new SendCommandAsync(getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, command, mWidgetLink);
                        mGoogleApiService.addMessageListener(SwitchWidgetActivity.this);
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
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (result.equals(mWidgetLink)) {
                        mCurrentState = !mCurrentState;
                        if (mCurrentState) {
                            mStatusTextView.setText("ON");
                        } else {
                            mStatusTextView.setText("OFF");
                        }
                    }
                }
            });
        }
    }
}

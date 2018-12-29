package org.openhab.habdroid.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.openhab.habdroid.util.Util.getHostFromUrl;

public class LogActivity extends AppCompatActivity {
    private static final String TAG = LogActivity.class.getSimpleName();

    private ProgressBar mProgressBar;
    private TextView mLogTextView;
    private FloatingActionButton mFab;
    private ScrollView mScrollView;
    private LinearLayout mEmptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mFab = findViewById(R.id.shareFab);
        mLogTextView = findViewById(R.id.log);
        mProgressBar = findViewById(R.id.progressBar);
        mScrollView = findViewById(R.id.scrollview);
        mEmptyView = findViewById(android.R.id.empty);

        mFab.setOnClickListener(v -> {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, mLogTextView.getText());
            sendIntent.setType("text/plain");
            startActivity(sendIntent);
        });

        setUiState(true, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUiState(true, false);
        new GetLogFromAdbTask().execute(false);
    }

    private void setUiState(boolean isLoading, boolean isEmpty) {
        mProgressBar.setVisibility(isLoading && !isEmpty ? View.VISIBLE : View.GONE);
        mLogTextView.setVisibility(isLoading && !isEmpty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isLoading || isEmpty) {
            mFab.hide();
        } else {
            mFab.show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected()");
        switch (item.getItemId()) {
            case R.id.delete_log:
                setUiState(true, false);
                new GetLogFromAdbTask().execute(true);
                return true;
            case android.R.id.home:
                finish();
                // fallthrough
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class GetLogFromAdbTask extends AsyncTask<Boolean, Void, String> {
        @Override
        protected String doInBackground(Boolean... clear) {
            StringBuilder logBuilder = new StringBuilder();
            String separator = System.getProperty("line.separator");
            Process process = null;
            try {
                if (clear[0]) {
                    Log.d(TAG, "Clear log");
                    Runtime.getRuntime().exec("logcat -b all -c");
                    return "";
                }
                process = Runtime.getRuntime().exec("logcat -b all -v threadtime -d");
            } catch (Exception e) {
                Log.e(TAG, "Error reading process", e);
            }
            if (process != null) {
                try (InputStreamReader reader = new InputStreamReader(process.getInputStream());
                        BufferedReader bufferedReader = new BufferedReader(reader)) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        logBuilder.append(line);
                        logBuilder.append(separator);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading log", e);
                }
            }
            String log = logBuilder.toString();
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String localUrl =
                    sharedPreferences.getString(Constants.PREFERENCE_LOCAL_URL, "");
            String remoteUrl =
                    sharedPreferences.getString(Constants.PREFERENCE_REMOTE_URL, "");
            if (!TextUtils.isEmpty(localUrl)) {
                log = log.replaceAll(getHostFromUrl(localUrl), "<openhab-local-address>");
            }
            if (!TextUtils.isEmpty(remoteUrl)) {
                log = log.replaceAll(getHostFromUrl(remoteUrl), "<openhab-remote-address>");
            }
            return log;
        }

        @Override
        protected void onPostExecute(String log) {
            mLogTextView.setText(log);
            setUiState(false, TextUtils.isEmpty(log));
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }
}

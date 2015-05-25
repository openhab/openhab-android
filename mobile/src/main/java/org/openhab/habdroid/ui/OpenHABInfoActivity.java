/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  @author Victor Belov
 *  @since 1.4.0
 *
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.Header;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.Util;

public class OpenHABInfoActivity extends Activity {

    private static final String TAG = "OpenHABInfoActivity";
    private TextView mOpenHABVersionText;
    private TextView mOpenHABUUIDText;
    private TextView mOpenHABSecretText;
    private TextView mOpenHABSecretLabel;
    private String mOpenHABBaseUrl;
    private String mUsername;
    private String mPassword;
    private static MyAsyncHttpClient mAsyncHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.openhabinfo);
        mAsyncHttpClient = new MyAsyncHttpClient(this);
        mOpenHABVersionText = (TextView)findViewById(R.id.openhab_version);
        mOpenHABUUIDText = (TextView)findViewById(R.id.openhab_uuid);
        mOpenHABSecretText = (TextView)findViewById(R.id.openhab_secret);
        mOpenHABSecretLabel = (TextView)findViewById(R.id.openhab_secret_label);
        if (getIntent().hasExtra("openHABBaseUrl")) {
            mOpenHABBaseUrl = getIntent().getStringExtra("openHABBaseUrl");
            mUsername = getIntent().getStringExtra("username");
            mPassword = getIntent().getStringExtra("password");
            mAsyncHttpClient.setBasicAuth(mUsername, mPassword);
        } else {
            Log.e(TAG, "No openHABBaseURl parameter passed, can't fetch openHAB info from nowhere");
            finish();
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        Log.d(TAG, "url = " + mOpenHABBaseUrl + "static/version");
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/version", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                mOpenHABVersionText.setText("Unknown");
                Log.e(TAG, error.getMessage());
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Log.d(TAG, "Got version = " + responseString);
                mOpenHABVersionText.setText(responseString);
            }
        });
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/uuid", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                mOpenHABUUIDText.setText("Unknown");
                if (error.getMessage() != null)
                    Log.e(TAG, error.getMessage());
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Log.d(TAG, "Got uuid = " + responseString);
                mOpenHABUUIDText.setText(responseString);
            }
        });
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/secret", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                mOpenHABSecretText.setVisibility(View.GONE);
                mOpenHABSecretLabel.setVisibility(View.GONE);
                Log.e(TAG, error.getMessage());
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Log.d(TAG, "Got secret = " + responseString);
                mOpenHABSecretText.setVisibility(View.VISIBLE);
                mOpenHABSecretLabel.setVisibility(View.VISIBLE);
                mOpenHABSecretText.setText(responseString);
            }
        });
    }

        @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }
}

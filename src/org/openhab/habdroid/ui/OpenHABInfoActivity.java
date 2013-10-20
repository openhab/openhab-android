/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.Util;

/**
 * Created by belovictor on 8/20/13.
 */

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
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/version", new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String content) {
                Log.d(TAG, "Got version = " + content);
                mOpenHABVersionText.setText(content);
            }

            @Override
            public void onFailure(Throwable error, String content) {
                mOpenHABVersionText.setText("Unknown");
                Log.e(TAG, error.getMessage());
            }
        });
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/uuid", new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String content) {
                Log.d(TAG, "Got uuid = " + content);
                mOpenHABUUIDText.setText(content);
            }

            @Override
            public void onFailure(Throwable error, String content) {
                mOpenHABUUIDText.setText("Unknown");
                Log.e(TAG, error.getMessage());
            }
        });
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/secret", new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String content) {
                Log.d(TAG, "Got secret = " + content);
                mOpenHABSecretText.setVisibility(View.VISIBLE);
                mOpenHABSecretLabel.setVisibility(View.VISIBLE);
                mOpenHABSecretText.setText(content);
            }

            @Override
            public void onFailure(Throwable error, String content) {
                mOpenHABSecretText.setVisibility(View.GONE);
                mOpenHABSecretLabel.setVisibility(View.GONE);
                Log.e(TAG, error.getMessage());
            }
        });
    }

        @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }
}

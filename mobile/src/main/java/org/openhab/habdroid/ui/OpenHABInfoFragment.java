/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.util.MyHttpClient;

import okhttp3.Call;
import okhttp3.Headers;


public class OpenHABInfoFragment extends Fragment {

    private static final String TAG = OpenHABInfoFragment.class.getSimpleName();
    private int mOpenHABVersion;
    private TextView mOpenHABVersionText;
    private TextView mOpenHABVersionLabel;
    private TextView mOpenHABUUIDText;
    private TextView mOpenHABSecretText;
    private TextView mOpenHABSecretLabel;
    private TextView mOpenHABNotificationText;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openhabinfo, container, false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences
                (getActivity().getApplicationContext());
        mOpenHABVersionText = (TextView)view.findViewById(R.id.openhab_version);
        mOpenHABUUIDText = (TextView)view.findViewById(R.id.openhab_uuid);
        mOpenHABSecretText = (TextView)view.findViewById(R.id.openhab_secret);
        mOpenHABSecretLabel = (TextView)view.findViewById(R.id.openhab_secret_label);
        mOpenHABVersionLabel = (TextView)view.findViewById(R.id.openhab_version_label);
        mOpenHABNotificationText = (TextView)view.findViewById(R.id.openhab_gcm);
        Bundle bundle = getArguments();

        if (bundle != null){
            mOpenHABVersion = bundle.getInt("openHABVersion");
        } else {
            Log.e(TAG, "No openHABBaseURl parameter passed, can't fetch openHAB info from nowhere");
        }

        return view;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        setVersionText();
        setUuidText();
        setSecretText();
        setGcmText();
    }

    private void setSecretText() {
        Connection conn;
        try {
            conn = ConnectionFactory.getConnection(Connection.TYPE_ANY, getActivity());
        } catch (NetworkNotSupportedException | NetworkNotAvailableException e) {
            return;
        }

        conn.getAsyncHttpClient().get("/static/secret", new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                mOpenHABSecretText.setVisibility(View.GONE);
                mOpenHABSecretLabel.setVisibility(View.GONE);
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                Log.d(TAG, "Got secret = " + responseString);
                mOpenHABSecretText.setVisibility(View.VISIBLE);
                mOpenHABSecretLabel.setVisibility(View.VISIBLE);
                mOpenHABSecretText.setText(responseString);
            }
        });
    }

    private void setUuidText() {
        Connection conn;
        try {
            conn = ConnectionFactory.getConnection(Connection.TYPE_ANY, getActivity());
        } catch (NetworkNotSupportedException | NetworkNotAvailableException e) {
            return;
        }

        final String uuidUrl;
        if (mOpenHABVersion == 1) {
            uuidUrl = "/static/uuid";
        } else {
            uuidUrl = "/rest/uuid";
        }
        conn.getAsyncHttpClient().get(uuidUrl, new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                mOpenHABUUIDText.setText(R.string.unknown);
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                Log.d(TAG, "Got uuid = " + responseString);
                mOpenHABUUIDText.setText(responseString);
            }
        });
    }

    private void setVersionText() {
        Connection conn;
        try {
            conn = ConnectionFactory.getConnection(Connection.TYPE_ANY, getActivity());
        } catch (NetworkNotSupportedException | NetworkNotAvailableException e) {
            return;
        }

        final String versionUrl;
        if (mOpenHABVersion == 1) {
            versionUrl = "/static/version";
        } else {
            versionUrl = "/rest";
        }
        Log.d(TAG, "url = " + versionUrl);
        conn.getAsyncHttpClient().get(versionUrl, new MyHttpClient.TextResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, String responseString, Throwable error) {
                mOpenHABVersionText.setText(R.string.unknown);
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, String responseString) {
                String version="";
                if(mOpenHABVersion == 1) {
                    version = responseString;
                    mOpenHABVersionLabel.setText(getResources().getText(R.string.info_openhab_version_label));
                } else {
                    try {
                        JSONObject pageJson = new JSONObject(responseString);
                        version = pageJson.getString("version");
                        mOpenHABVersionLabel.setText(getResources().getText(R.string.info_openhab_apiversion_label));
                    } catch (JSONException e) {
                        Log.e(TAG, "Problem fetching version string");
                    }
                }
                Log.d(TAG, "Got version = " + version);
                mOpenHABVersionText.setText(version);
            }
        });
    }


    private void setGcmText() {
        if (OpenHABMainActivity.GCM_SENDER_ID == null) {
            mOpenHABNotificationText.setText(R.string.info_openhab_gcm_not_connected);
        } else {
            mOpenHABNotificationText.setText(
                    getString(R.string.info_openhab_gcm_connected, OpenHABMainActivity.GCM_SENDER_ID));
        }
    }
}

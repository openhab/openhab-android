/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;


import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import com.loopj.android.http.TextHttpResponseHandler;

import cz.msebera.android.httpclient.Header;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.MyAsyncHttpClient;


public class OpenHABInfoFragment extends DialogFragment {

    private static final String TAG = OpenHABInfoFragment.class.getSimpleName();
    private int mOpenHABVersion;
    private TextView mOpenHABVersionText;
    private TextView mOpenHABVersionLabel;
    private TextView mOpenHABUUIDText;
    private TextView mOpenHABSecretText;
    private TextView mOpenHABSecretLabel;
    private String mOpenHABBaseUrl;
    private String mUsername;
    private String mPassword;
    private static MyAsyncHttpClient mAsyncHttpClient;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openhabinfo, container);
        mAsyncHttpClient = new MyAsyncHttpClient(getActivity().getApplicationContext());
        mOpenHABVersionText = (TextView)view.findViewById(R.id.openhab_version);
        mOpenHABUUIDText = (TextView)view.findViewById(R.id.openhab_uuid);
        mOpenHABSecretText = (TextView)view.findViewById(R.id.openhab_secret);
        mOpenHABSecretLabel = (TextView)view.findViewById(R.id.openhab_secret_label);
        mOpenHABVersionLabel = (TextView)view.findViewById(R.id.openhab_version_label);
        Bundle bundle=getArguments();

        if (bundle!=null){

            mOpenHABBaseUrl = bundle.getString("openHABBaseUrl");
            mUsername = bundle.getString("username");
            mPassword = bundle.getString("password");
            mOpenHABVersion = bundle.getInt("openHABVersion");
            mAsyncHttpClient.setBasicAuth(mUsername, mPassword);
        } else {
            Log.e(TAG, "No openHABBaseURl parameter passed, can't fetch openHAB info from nowhere");

        }


        return view;
    }
    @Override
    public void onStart()
    {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {

            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }


    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        setVersionText();
        setUuidText();
        setSecretText();
    }

    private void setSecretText() {
        mAsyncHttpClient.get(mOpenHABBaseUrl + "static/secret", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                mOpenHABSecretText.setVisibility(View.GONE);
                mOpenHABSecretLabel.setVisibility(View.GONE);
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
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

    private void setUuidText() {
        String uuidUrl;
        if (mOpenHABVersion == 1) {
            uuidUrl = mOpenHABBaseUrl + "static/uuid";
        } else {
            uuidUrl = mOpenHABBaseUrl + "rest/uuid";
        }
        mAsyncHttpClient.get(uuidUrl, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                mOpenHABUUIDText.setText("Unknown");
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Log.d(TAG, "Got uuid = " + responseString);
                mOpenHABUUIDText.setText(responseString);
            }
        });
    }

    private void setVersionText() {
        final String versionUrl;
        if (mOpenHABVersion == 1) {
            versionUrl = mOpenHABBaseUrl + "static/version";
        } else {
            versionUrl = mOpenHABBaseUrl + "rest";
        }
        Log.d(TAG, "url = " + versionUrl);
        mAsyncHttpClient.get(versionUrl, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                mOpenHABVersionText.setText("Unknown");
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
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


}

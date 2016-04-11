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

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.Header;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.Util;

public class OpenHABInfoFragment extends DialogFragment {

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openhabinfo, container);
        mAsyncHttpClient = new MyAsyncHttpClient(getActivity().getApplicationContext());
        mOpenHABVersionText = (TextView)view.findViewById(R.id.openhab_version);
        mOpenHABUUIDText = (TextView)view.findViewById(R.id.openhab_uuid);
        mOpenHABSecretText = (TextView)view.findViewById(R.id.openhab_secret);
        mOpenHABSecretLabel = (TextView)view.findViewById(R.id.openhab_secret_label);
        Bundle bundle=getArguments();

        if (bundle!=null)
        {

            mOpenHABBaseUrl = bundle.getString("openHABBaseUrl");
            mUsername = bundle.getString("username");
            mPassword = bundle.getString("password");
            mAsyncHttpClient.setBasicAuth(mUsername, mPassword);
        }else {
            Log.e(TAG, "No openHABBaseURl parameter passed, can't fetch openHAB info from nowhere");

        }


        return view;
    }
    @Override
    public void onStart()
    {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null)
        {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
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
                if (error.getMessage() != null) {
                    Log.e(TAG, error.getMessage());
                }
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


}

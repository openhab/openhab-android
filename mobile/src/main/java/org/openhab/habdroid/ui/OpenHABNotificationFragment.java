/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABNotification;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Headers;

public class OpenHABNotificationFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = OpenHABNotificationFragment.class.getSimpleName();

    private static final String ARG_USERNAME = "openHABUsername";
    private static final String ARG_PASSWORD = "openHABPassword";
    private static final String ARG_BASEURL = "openHABBaseUrl";

    private String openHABUsername = "";
    private String openHABPassword = "";
    private String openHABBaseURL = "";

    private OpenHABMainActivity mActivity;
    // loopj
    private MyAsyncHttpClient mAsyncHttpClient;
    // keeps track of current request to cancel it in onPause
    private Call mRequestHandle;

    private OpenHABNotificationAdapter mNotificationAdapter;
    private ArrayList<OpenHABNotification> mNotifications;

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeLayout;

    public static OpenHABNotificationFragment newInstance(String baseURL, String username, String password) {
        OpenHABNotificationFragment fragment = new OpenHABNotificationFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        args.putString(ARG_PASSWORD, password);
        args.putString(ARG_BASEURL, baseURL);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public OpenHABNotificationFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        mNotifications = new ArrayList<OpenHABNotification>();
        if (getArguments() != null) {
            openHABUsername = getArguments().getString(ARG_USERNAME);
            openHABPassword = getArguments().getString(ARG_PASSWORD);
            openHABBaseURL =  getArguments().getString(ARG_BASEURL);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        Log.d(TAG, "isAdded = " + isAdded());
        View view = inflater.inflate(R.layout.openhabnotificationlist_fragment, container, false);
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        mRecyclerView = view.findViewById(android.R.id.list);
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "onAttach()");
        try {
            mActivity = (OpenHABMainActivity) activity;
            mAsyncHttpClient = mActivity.getAsyncHttpClient();
            mActivity.setTitle(R.string.app_notifications);
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must be OpenHABMainActivity");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mNotificationAdapter = new OpenHABNotificationAdapter(mActivity, mNotifications,
                openHABBaseURL, openHABUsername, openHABPassword);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mActivity));
        mRecyclerView.setAdapter(mNotificationAdapter);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        Log.d(TAG, "isAdded = " + isAdded());
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        loadNotifications();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        // Cancel request for notifications if there was any
        if (mRequestHandle != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    mRequestHandle.cancel();
                }
            });
            thread.start();
        }
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "onRefresh()");
        refresh();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach()");
        mActivity = null;
    }

    public void refresh() {
        Log.d(TAG, "refresh()");
        loadNotifications();
    }

    private void loadNotifications() {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mRequestHandle = mAsyncHttpClient.get(openHABBaseURL + "/api/v1/notifications?limit=20", new MyHttpClient.ResponseHandler() {
                @Override
                public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                    stopProgressIndicator();
                    Log.d(TAG, "Notifications request success");
                    try {
                        String jsonString = new String(responseBody, "UTF-8");
                        JSONArray jsonArray = new JSONArray(jsonString);
                        Log.d(TAG, jsonArray.toString());
                        mNotifications.clear();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            try {
                                JSONObject sitemapJson = jsonArray.getJSONObject(i);
                                OpenHABNotification notification = new OpenHABNotification(sitemapJson);
                                mNotifications.add(notification);
                            } catch (JSONException e) {
                                Log.d(TAG, e.getMessage(), e);
                            }
                        }
                        mNotificationAdapter.notifyDataSetChanged();
                    } catch (UnsupportedEncodingException | JSONException e) {
                        Log.d(TAG, e.getMessage(), e);
                    }
                }

                @Override
                public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.d(TAG, "Notifications request failure");
                }
            });
        }
    }

    private void stopProgressIndicator() {
        if (mActivity != null) {
            Log.d(TAG, "Stop progress indicator");
            mActivity.setProgressIndicatorVisible(false);
        }
    }

    private void startProgressIndicator() {
        if (mActivity != null) {
            Log.d(TAG, "Start progress indicator");
            mActivity.setProgressIndicatorVisible(true);
        }
        mSwipeLayout.setRefreshing(false);
    }
}

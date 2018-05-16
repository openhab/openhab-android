/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.model.OpenHABNotification;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.AsyncHttpClient;

import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;

public class OpenHABNotificationFragment extends Fragment implements
        View.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = OpenHABNotificationFragment.class.getSimpleName();

    private OpenHABMainActivity mActivity;
    // keeps track of current request to cancel it in onPause
    private Call mRequestHandle;

    private OpenHABNotificationAdapter mNotificationAdapter;
    private ArrayList<OpenHABNotification> mNotifications;

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeLayout;
    private View mEmptyView;
    private ImageView mEmptyWatermark;
    private TextView mEmptyMessage;
    private View mRetryButton;

    public static OpenHABNotificationFragment newInstance() {
        return new OpenHABNotificationFragment();
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
        mNotifications = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.openhabnotificationlist_fragment, container, false);
        mSwipeLayout = view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        mRecyclerView = view.findViewById(android.R.id.list);
        mEmptyView = view.findViewById(android.R.id.empty);
        mEmptyMessage = view.findViewById(R.id.empty_message);
        mEmptyWatermark = view.findViewById(R.id.watermark);
        mRetryButton = view.findViewById(R.id.retry_button);
        mRetryButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (OpenHABMainActivity) getActivity();
        mNotificationAdapter = new OpenHABNotificationAdapter(mActivity, mNotifications);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mActivity));
        mRecyclerView.setAdapter(mNotificationAdapter);
        Log.d(TAG, "onActivityCreated()");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
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
            new Thread(() -> mRequestHandle.cancel()).start();
        }
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "onRefresh()");
        loadNotifications();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach()");
        mActivity = null;
    }

    @Override
    public void onClick(View view) {
        if (view == mRetryButton) {
            loadNotifications();
        }
    }

    private void loadNotifications() {
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (conn == null) {
            updateViewVisibility(false, true);
            return;
        }
        updateViewVisibility(true, false);
        mRequestHandle = conn.getAsyncHttpClient().get("api/v1/notifications?limit=20",
                new AsyncHttpClient.StringResponseHandler() {
            @Override
            public void onSuccess(String responseBody, Headers headers) {
                Log.d(TAG, "Notifications request success");
                try {
                    JSONArray jsonArray = new JSONArray(responseBody);
                    Log.d(TAG, jsonArray.toString());
                    mNotifications.clear();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject sitemapJson = jsonArray.getJSONObject(i);
                        mNotifications.add(OpenHABNotification.fromJson(sitemapJson));
                    }
                    mNotificationAdapter.notifyDataSetChanged();
                    updateViewVisibility(false, false);
                } catch (JSONException e) {
                    Log.d(TAG, e.getMessage(), e);
                    updateViewVisibility(false, true);
                }
            }

            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                updateViewVisibility(false, true);
                Log.e(TAG, "Notifications request failure");
            }
        });
    }

    private void updateViewVisibility(boolean loading, boolean loadError) {
        boolean showEmpty = !loading && (mNotificationAdapter.getItemCount() == 0 || loadError);
        mRecyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        mSwipeLayout.setRefreshing(loading);
        mEmptyMessage.setText(
                loadError ? R.string.notification_list_error : R.string.notification_list_empty);
        mEmptyWatermark.setImageResource(
                loadError ? R.drawable.ic_connection_error : R.drawable.ic_no_notifications);
        mRetryButton.setVisibility(loadError ? View.VISIBLE : View.GONE);
    }
}

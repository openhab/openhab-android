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
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.model.CloudNotification;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;
import org.openhab.habdroid.util.AsyncHttpClient;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.Locale;

public class CloudNotificationListFragment extends Fragment implements
        View.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = CloudNotificationListFragment.class.getSimpleName();

    private static final int PAGE_SIZE = 20;

    private MainActivity mActivity;
    // keeps track of current request to cancel it in onPause
    private Call mRequestHandle;

    private CloudNotificationAdapter mNotificationAdapter;
    private String mInitiallyHighlightedId;

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private SwipeRefreshLayout mSwipeLayout;
    private View mEmptyView;
    private ImageView mEmptyWatermark;
    private TextView mEmptyMessage;
    private View mRetryButton;
    private int mLoadOffset;

    public static CloudNotificationListFragment newInstance(@Nullable String highlightedId) {
        CloudNotificationListFragment f = new CloudNotificationListFragment();
        Bundle args = new Bundle();
        args.putString("highlightedId", highlightedId);
        f.setArguments(args);
        return f;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CloudNotificationListFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_notificationlist, container, false);
        mSwipeLayout = view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        Util.applySwipeLayoutColors(mSwipeLayout, R.attr.colorPrimary, R.attr.colorAccent);

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
        mActivity = (MainActivity) getActivity();
        mNotificationAdapter = new CloudNotificationAdapter(mActivity,
                () -> loadNotifications(false));
        mLayoutManager = new LinearLayoutManager(mActivity);

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mActivity));
        mRecyclerView.setAdapter(mNotificationAdapter);
        Log.d(TAG, "onActivityCreated()");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        loadNotifications(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        // Cancel request for notifications if there was any
        if (mRequestHandle != null) {
            mRequestHandle.cancel();
        }
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "onRefresh()");
        loadNotifications(true);
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
            loadNotifications(true);
        }
    }

    private void loadNotifications(boolean clearExisting) {
        Connection conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (conn == null) {
            updateViewVisibility(false, true);
            return;
        }
        if (clearExisting) {
            mNotificationAdapter.clear();
            mLoadOffset = 0;
            updateViewVisibility(true, false);
        }

        // If we're passed an ID to be highlighted initially, we'd theoretically need to load all
        // items instead of loading page-wise. As the initial highlight is only needed for
        // notifications and a new notification is very likely to be contained in the first page,
        // we skip that additional effort.
        final String url = String.format(Locale.US, "api/v1/notifications?limit=%d&skip=%d",
                PAGE_SIZE, mLoadOffset);
        final AsyncHttpClient client = conn.getAsyncHttpClient();
        mRequestHandle = client.get(url, new AsyncHttpClient.StringResponseHandler() {
            @Override
            public void onSuccess(String responseBody, Headers headers) {
                try {
                    ArrayList<CloudNotification> items = new ArrayList<>();
                    JSONArray jsonArray = new JSONArray(responseBody);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject sitemapJson = jsonArray.getJSONObject(i);
                        items.add(CloudNotification.fromJson(sitemapJson));
                    }
                    Log.d(TAG, "Notifications request success, got " + items.size() + " items");
                    mLoadOffset += items.size();
                    mNotificationAdapter.addLoadedItems(items, items.size() == PAGE_SIZE);
                    handleInitialHighlight();
                    updateViewVisibility(false, false);
                } catch (JSONException e) {
                    Log.d(TAG, "Notification response could not be parsed", e);
                    updateViewVisibility(false, true);
                }
            }

            @Override
            public void onFailure(Request request, int statusCode, Throwable error) {
                updateViewVisibility(false, true);
                Log.e(TAG, "Notifications request failure", error);
            }
        });
    }

    private void handleInitialHighlight() {
        Bundle args = getArguments();
        String highlightedId = args.getString("highlightedId");
        if (TextUtils.isEmpty(highlightedId)) {
            return;
        }

        final int position = mNotificationAdapter.findPositionForId(highlightedId);
        if (position >= 0) {
            mLayoutManager.scrollToPositionWithOffset(position, 0);
            mRecyclerView.postDelayed(() -> mNotificationAdapter.highlightItem(position), 600);
        }

        // highlight only once
        args.remove("highlightedId");
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

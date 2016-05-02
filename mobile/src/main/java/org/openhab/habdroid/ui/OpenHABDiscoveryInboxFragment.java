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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.software.shell.fab.ActionButton;

import cz.msebera.android.httpclient.Header;
import org.openhab.habdroid.R;

import org.openhab.habdroid.model.OpenHABDiscoveryInbox;
import org.openhab.habdroid.model.thing.ThingType;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

public class OpenHABDiscoveryInboxFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = OpenHABDiscoveryInboxFragment.class.getSimpleName();

    private static final String ARG_USERNAME = "openHABUsername";
    private static final String ARG_PASSWORD = "openHABPassword";
    private static final String ARG_BASEURL = "openHABBaseUrl";

    private String openHABUsername = "";
    private String openHABPassword = "";
    private String openHABBaseUrl = "";

    private OpenHABMainActivity mActivity;
    // loopj
    private AsyncHttpClient mAsyncHttpClient;
    // keeps track of current request to cancel it in onPause
    private RequestHandle mRequestHandle;

    private OpenHABDiscoveryInboxAdapter mDiscoveryInboxAdapter;
    private ArrayList<OpenHABDiscoveryInbox> mDiscoveryInbox;
    private ArrayList<ThingType> mThingTypes;

    private SwipeRefreshLayout mSwipeLayout;

    private int selectedInbox;

    private ActionButton discoveryButton;

    public static OpenHABDiscoveryInboxFragment newInstance(String baseUrl, String username, String password) {
        OpenHABDiscoveryInboxFragment fragment = new OpenHABDiscoveryInboxFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        args.putString(ARG_PASSWORD, password);
        args.putString(ARG_BASEURL, baseUrl);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public OpenHABDiscoveryInboxFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        mDiscoveryInbox = new ArrayList<OpenHABDiscoveryInbox>();
        mThingTypes = new ArrayList<ThingType>();
        if (getArguments() != null) {
            openHABUsername = getArguments().getString(ARG_USERNAME);
            openHABPassword = getArguments().getString(ARG_PASSWORD);
            openHABBaseUrl = getArguments().getString(ARG_BASEURL);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        Log.d(TAG, "isAdded = " + isAdded());
        View view = inflater.inflate(R.layout.openhabdiscoveryinboxlist_fragment, container, false);
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        discoveryButton = (ActionButton)view.findViewById(R.id.discovery_button);
        if (discoveryButton != null) {
            discoveryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Discovery button pressed");
                    if (mActivity != null) {
                        mActivity.openDiscovery();
                    }
                }
            });
        }
        return view;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "onAttach()");
        try {
            mActivity = (OpenHABMainActivity) activity;
            mAsyncHttpClient = mActivity.getAsyncHttpClient();
            mActivity.setTitle(R.string.app_discoveryinbox);
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must be OpenHABMainActivity");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mDiscoveryInboxAdapter = new OpenHABDiscoveryInboxAdapter(this.getActivity(), R.layout.openhabdiscoveryinboxlist_item, mDiscoveryInbox);
        getListView().setAdapter(mDiscoveryInboxAdapter);
        getListView().setEmptyView(getActivity().findViewById(R.id.empty_inbox_view));
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
    }

    @Override
    public void onResume () {
        super.onResume();
        Log.d(TAG, "onResume()");
        loadDiscoveryInbox();
        loadThingTypes();
    }

    @Override
    public void onPause () {
        super.onPause();
        Log.d(TAG, "onPause()");
        // Cancel request for notifications if there was any
        if (mRequestHandle != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    mRequestHandle.cancel(true);
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
        loadDiscoveryInbox();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        selectedInbox = position;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.app_discoveryinbox_approve, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                sendInboxApprove(mDiscoveryInboxAdapter.getItem(selectedInbox).getThingUID());
            }
        });
        builder.setNeutralButton(R.string.app_discoveryinbox_ignore, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                sendInboxIgnore(mDiscoveryInboxAdapter.getItem(selectedInbox).getThingUID());
            }
        });
        builder.setNegativeButton(R.string.app_discoveryinbox_delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                sendInboxDelete(mDiscoveryInboxAdapter.getItem(selectedInbox).getThingUID());
            }
        });
        builder.setCancelable(true);
        builder.setTitle(R.string.app_discoveryinbox_deviceaction);
        builder.show();
    }

    private void loadDiscoveryInbox() {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mRequestHandle = mAsyncHttpClient.get(openHABBaseUrl + "rest/inbox", new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    stopProgressIndicator();
                    String jsonString = null;
                    try {
                        jsonString = new String(responseBody, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "Inbox request success");
                    Log.d(TAG, jsonString);
                    GsonBuilder gsonBuilder = new GsonBuilder();
                    Gson gson = gsonBuilder.create();
                    mDiscoveryInbox.clear();
                    mDiscoveryInbox.addAll(Arrays.asList(gson.fromJson(jsonString, OpenHABDiscoveryInbox[].class)));
                    mDiscoveryInboxAdapter.notifyDataSetChanged();
                }

                @Override
                public void  onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.d(TAG, "Inbox request failure: " + error.getMessage());
                }
            });
        }
    }

    private void loadThingTypes () {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mRequestHandle = mAsyncHttpClient.get(openHABBaseUrl + "rest/thing-types", new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    stopProgressIndicator();
                    String jsonString = null;
                    try {
                        jsonString = new String(responseBody, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "Thing types request success");
                    Log.d(TAG, jsonString);
                    GsonBuilder gsonBuilder = new GsonBuilder();
                    Gson gson = gsonBuilder.create();
                    mThingTypes.clear();
                    mThingTypes.addAll(Arrays.asList(gson.fromJson(jsonString, ThingType[].class)));
                    mDiscoveryInboxAdapter.setThingTypes(mThingTypes);
                    mDiscoveryInboxAdapter.notifyDataSetChanged();
                }

                @Override
                public void  onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.d(TAG, "Thing types request failure: " + error.getMessage());
                }
            });
        }
    }

    private void sendInboxApprove(String UID) {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mAsyncHttpClient.post(getActivity(), openHABBaseUrl + "rest/inbox/" + UID + "/approve", null, "text/plain", new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    stopProgressIndicator();
                    Log.d(TAG, "Inbox approve request success");
                    refresh();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.e(TAG, "Inbox approve request error: " + error.getMessage());
                }
            });
        }
    }

    private void sendInboxIgnore(String UID) {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mAsyncHttpClient.post(getActivity(), openHABBaseUrl + "rest/inbox/" + UID + "/ignore", null, "text/plain", new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    stopProgressIndicator();
                    Log.d(TAG, "Inbox ignore request success");
                    refresh();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.e(TAG, "Inbox ignore request error: " + error.getMessage());
                }
            });
        }
    }


    private void sendInboxDelete(String UID) {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mAsyncHttpClient.delete(getActivity(), openHABBaseUrl + "rest/inbox/" + UID, new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    stopProgressIndicator();
                    Log.d(TAG, "Inbox delete request success");
                    refresh();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.e(TAG, "Inbox delete request error: " + error.getMessage());
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

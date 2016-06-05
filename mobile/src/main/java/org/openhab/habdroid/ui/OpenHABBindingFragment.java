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

import cz.msebera.android.httpclient.Header;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBinding;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

public class OpenHABBindingFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = OpenHABBindingFragment.class.getSimpleName();

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

    private OpenHABBindingAdapter mBindingAdapter;
    private ArrayList<OpenHABBinding> mBindings;

    private SwipeRefreshLayout mSwipeLayout;

    public static OpenHABBindingFragment newInstance(String baseUrl, String username, String password) {
        OpenHABBindingFragment fragment = new OpenHABBindingFragment();
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
    public OpenHABBindingFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        mBindings = new ArrayList<OpenHABBinding>();
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
        View view = inflater.inflate(R.layout.openhabbindinglist_fragment, container, false);
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        return view;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "onAttach()");
        try {
            mActivity = (OpenHABMainActivity) activity;
            mAsyncHttpClient = mActivity.getAsyncHttpClient();
            mActivity.setTitle(R.string.app_bindings);
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must be OpenHABMainActivity");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mBindingAdapter = new OpenHABBindingAdapter(this.getActivity(), R.layout.openhabbindinglist_item, mBindings);
        getListView().setAdapter(mBindingAdapter);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        loadBindings();
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
        loadBindings();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
//        super.onListItemClick(l, v, position, id);
        if (mBindings.get(position).getThingTypes() == null) {
            Log.d(TAG, "Source thing types == null");
            return;
        }
        if (mActivity != null) {
            mActivity.openBindingThingTypes(mBindings.get(position).getThingTypes());
        }
    }

    private void loadBindings() {
        if (mAsyncHttpClient != null) {
            startProgressIndicator();
            mRequestHandle = mAsyncHttpClient.get(openHABBaseUrl + "rest/bindings", new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    stopProgressIndicator();
                    String jsonString = null;
                    try {
                        jsonString = new String(responseBody, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "Bindings request success");
                    Log.d(TAG, jsonString);
                    GsonBuilder gsonBuilder = new GsonBuilder();
                    Gson gson = gsonBuilder.create();
                    mBindings.clear();
                    mBindings.addAll(Arrays.asList(gson.fromJson(jsonString, OpenHABBinding[].class)));
                    mBindingAdapter.notifyDataSetChanged();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    stopProgressIndicator();
                    Log.d(TAG, "Bindings request failure: " + error.getMessage());
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

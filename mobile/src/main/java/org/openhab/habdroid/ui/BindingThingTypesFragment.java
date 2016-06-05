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

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestHandle;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.thing.ThingType;

import java.util.ArrayList;

public class BindingThingTypesFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = BindingThingTypesFragment.class.getSimpleName();

    private static final String ARG_THINGTYPES = "thingTypes";

    private OpenHABMainActivity mActivity;
    // loopj
    private AsyncHttpClient mAsyncHttpClient;
    // keeps track of current request to cancel it in onPause
    private RequestHandle mRequestHandle;

    private BindingThingTypesAdapter bindingThingTypesAdapter;
    private ArrayList<ThingType> thingTypes;

    private SwipeRefreshLayout mSwipeLayout;

    public static BindingThingTypesFragment newInstance(ArrayList<ThingType> thingTypes) {
        BindingThingTypesFragment fragment = new BindingThingTypesFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_THINGTYPES, thingTypes);
        fragment.setArguments(args);
        return fragment;
    }

    public BindingThingTypesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        if (getArguments() != null) {
            thingTypes = getArguments().getParcelableArrayList(ARG_THINGTYPES);

            if (thingTypes == null) {
                Log.d(TAG, "thingTypes == null");
            } else {
                for (ThingType thingType : thingTypes) {
                    Log.d(TAG, "Got thingType " + thingType.getLabel());
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        Log.d(TAG, "isAdded = " + isAdded());
        View view = inflater.inflate(R.layout.bindingthingtypeslist_fragment, container, false);
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
            mActivity.setTitle(R.string.app_bindings_supportedthings);
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must be OpenHABMainActivity");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        bindingThingTypesAdapter = new BindingThingTypesAdapter(this.getActivity(), R.layout.bindingthingtypeslist_item, thingTypes);
        getListView().setAdapter(bindingThingTypesAdapter);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
    }

    @Override
    public void onResume() {
        super.onResume();
        bindingThingTypesAdapter.notifyDataSetChanged();
        Log.d(TAG, "onResume()");
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
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
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

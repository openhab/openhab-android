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
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;

import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABBeacons;
import org.openhab.habdroid.util.BeaconHandler;
import org.openhab.habdroid.util.Util;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.Header;

public class OpenHABNearRoomFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = OpenHABNearRoomFragment.class.getSimpleName();

    private static final String ARG_USERNAME = "openHABUsername";
    private static final String ARG_PASSWORD = "openHABPassword";
    private static final String ARG_BASEURL = "openHABBaseUrl";
    private static final String NOT_LOCATE = "Locate is not activated, all linked rooms will be shown";
    private static final String BLUETOOTH_IS_NOT_ACTIVATED = "Bluetooth is not activated, all linked rooms will be shown";
    private static final String IS_NO_BLE_DEVICE = "Your device do not support BLE, all linked rooms will be shown";

    private String openHABUsername = "";
    private String openHABPassword = "";
    private String openHABBaseUrl = "";
    // loopj
    private AsyncHttpClient mAsyncHttpClient;
    // keeps track of current request to cancel it in onPause
    private RequestHandle mRequestHandle;

    private OpenHABNearRoomAdapter mNearRoomAdapter;
    private ArrayList<OpenHABBeacons> shownBeacons;
    private OpenHABBeacons noBeacon;

    private TextView nonLocate;
    private ListView nearromList;

    private BeaconHandler beaconHandler;

    private SwipeRefreshLayout mSwipeLayout;

    public static OpenHABNearRoomFragment newInstance(String baseUrl, String username, String password) {
        OpenHABNearRoomFragment fragment = new OpenHABNearRoomFragment();
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
    public OpenHABNearRoomFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        shownBeacons = new ArrayList<>();
        if (getArguments() != null) {
            openHABUsername = getArguments().getString(ARG_USERNAME);
            openHABPassword = getArguments().getString(ARG_PASSWORD);
            openHABBaseUrl = getArguments().getString(ARG_BASEURL);
        }
        nonLocate = new TextView(getContext());
        nonLocate.setTextSize(18);
        beaconHandler = BeaconHandler.getInstance(getContext());
        noBeacon = new OpenHABBeacons("No Beacon Seen", "Serching for more", "Dummy", -1.0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        Log.d(TAG, "isAdded = " + isAdded());
        View view = inflater.inflate(R.layout.openhabnearroomlist_fragment, container, false);
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        nearromList = (ListView)view.findViewWithTag("near_rooms_list");
        nearromList.addHeaderView(nonLocate);
        return view;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "onAttach()");
        if(activity.equals(getActivity())){
            Log.i(TAG, "onAttach: Activities are Equal");
        }
        try {
            OpenHABMainActivity mainActivity = (OpenHABMainActivity)getActivity();
            mAsyncHttpClient = mainActivity.getAsyncHttpClient();
            mainActivity.setTitle(R.string.app_nearrooms);
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must be OpenHABMainActivity");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mNearRoomAdapter = new OpenHABNearRoomAdapter(this.getActivity(), R.layout.openhabnearroomlist_item, shownBeacons);
        getListView().setAdapter(mNearRoomAdapter);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        ((OpenHABMainActivity)getActivity()).setNearRoomFragment(this);
        refresh();
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
    }

    public void refresh() {
        // A refresh can be executed while the activity is reinstanciated
        Log.d(TAG, "refresh()");
        handleHeader();
        handleShownBeacons();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // The Header with information about Bluetooth should do nothing if the User click on it
        // The Header has id -1 so only items with ID >= 0 will get an action.
        if(id >= 0){
            OpenHABBeacons beacon = (OpenHABBeacons) l.getItemAtPosition(position);
            /* TODO: Add function to bind Becon to a sitemap over Near Rooms View
            if(beacon.getRoomForView() == null){
                mainActivity.createBeacon(beacon);
            }
            else*/ if(beacon.getRoomForView() != null && !"".equals(beacon.getSitemap()) && !"".equals(beacon.getGroup())) {
                String beaconURL = openHABBaseUrl + "rest/sitemaps/" + beacon.getSitemap() + "/" + beacon.getGroup();
                Log.d(TAG, "onListItemClick: " + beaconURL);
                ((OpenHABMainActivity)getActivity()).openBeaconPage(beaconURL);
            }
        }
    }

    private void handleShownBeacons(){
        List<OpenHABBeacons> knownBeacons = beaconHandler.getKnownBeacons();
        shownBeacons.clear();
        if(OpenHABMainActivity.isBLEDevice()<0 || !OpenHABMainActivity.bluetoothActivated || !OpenHABMainActivity.isLocate()){
            shownBeacons.addAll(knownBeacons);
            noBeacon.resetNotSeen();
        }
        else{
            List<OpenHABBeacons> nearBeacons = beaconHandler.getNearRooms();
            if(nearBeacons.isEmpty()){
                noBeacon.incrementNotSeen();
                shownBeacons.add(noBeacon);
            }
            else{
                noBeacon.resetNotSeen();
                for(OpenHABBeacons nearBeacon : nearBeacons) {
                    if (knownBeacons.contains(nearBeacon)){
                        shownBeacons.add(nearBeacon.addHABInfos(knownBeacons.get(knownBeacons.indexOf(nearBeacon))));
                    }
                    else{
                        nearBeacon.setName("<UNKNOWN>");
                        shownBeacons.add(nearBeacon);
                    }
                }
            }
        }
        Log.d(TAG, "handleShownBeacons: " + shownBeacons.size());
        mNearRoomAdapter.notifyDataSetChanged();
    }

    private void handleHeader(){
        nonLocate.setVisibility(View.VISIBLE);
        if(OpenHABMainActivity.isBLEDevice()<0){
            nonLocate.setText(IS_NO_BLE_DEVICE);
        }
        else if(!OpenHABMainActivity.bluetoothActivated){
            nonLocate.setText(BLUETOOTH_IS_NOT_ACTIVATED);
        }
        else if(!OpenHABMainActivity.isLocate()){
            nonLocate.setText(NOT_LOCATE);
        }
        else{
            nonLocate.setVisibility(View.INVISIBLE);
        }
    }
}

package org.openhab.habdroid.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.GeofencingService;
import org.openhab.habdroid.model.OpenHABGeofence;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;

import java.util.ArrayList;

import okhttp3.Call;

public class OpenHABGeofenceFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = OpenHABGeofenceFragment.class.getSimpleName();
    public static final String ACTION_UPDATE_VIEW = "ACTION_UPDATE_VIEW";

    private OpenHABMainActivity mActivity;
    // keeps track of current request to cancel it in onPause
    private Call mRequestHandle;

    private OpenHABGeofenceAdapter mGeofenceAdapter;
    private ArrayList<OpenHABGeofence> mGeofences;

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeLayout;
    private FloatingActionButton mAddGeofenceFAB;

    public static OpenHABGeofenceFragment newInstance() {
        OpenHABGeofenceFragment fragment = new OpenHABGeofenceFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public OpenHABGeofenceFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        mGeofences = new ArrayList<OpenHABGeofence>();
    }

    int i = 0;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        //Log.i(TAG, "onCreateView");
        //Log.d(TAG, "isAdded = " + isAdded());
        View view = inflater.inflate(R.layout.openhabgeofenceslist_fragment, container, false);
        mAddGeofenceFAB = view.findViewById(R.id.addGeofenceFAB);
        mAddGeofenceFAB.setOnClickListener(v -> {
            Log.i(TAG, "New Geofence");
            GeofencingService.addGeofence(getActivity(),new OpenHABGeofence(42,42,"42","fortytwo"+i++));
        });
        mSwipeLayout = view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        mRecyclerView = view.findViewById(android.R.id.list);


        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (OpenHABMainActivity) getActivity();
        mGeofenceAdapter = new OpenHABGeofenceAdapter(mActivity, mGeofences);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mActivity));
        mRecyclerView.setAdapter(mGeofenceAdapter);

        //Log.d(TAG, "onActivityCreated()");
        //Log.d(TAG, "isAdded = " + isAdded());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        Log.d(TAG, "isAdded = " + isAdded());
        super.onViewCreated(view, savedInstanceState);
    }

    BroadcastReceiver updateViewBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadGeofences();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        //Log.d(TAG, "onResume()");

        LocalBroadcastManager.getInstance(getActivity().getApplicationContext()).registerReceiver(updateViewBroadcastReceiver,new IntentFilter(ACTION_UPDATE_VIEW));
        loadGeofences();
    }

    @Override
    public void onPause() {
        super.onPause();
        //Log.d(TAG, "onPause()");
        LocalBroadcastManager.getInstance(getActivity().getApplicationContext()).unregisterReceiver(updateViewBroadcastReceiver);
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
        //Log.d(TAG, "onRefresh()");
        loadGeofences();
        mSwipeLayout.setRefreshing(false);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //Log.d(TAG, "onDetach()");
        mActivity = null;
    }

    /*@Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.geofence_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.delete:
                GeofencingService.removeGeofence(getActivity(),GeofencingService.getGeofences(getActivity()).get(info.position));
                return true;
            case R.id.copy:
                showToast("Feature not implemented yet");
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }*/

    private void loadGeofences() {
        mGeofences.clear();
        mGeofences.addAll(GeofencingService.getGeofences(getActivity()));
        //mGeofences.add(new OpenHABGeofence(30.897957, -77.036560,100,"White House"));
        mGeofenceAdapter.notifyDataSetChanged();

//            mRequestHandle = conn.getAsyncHttpClient().get("api/v1/notifications?limit=20",
//                    new AsyncHttpClient.StringResponseHandler() {
//                        @Override
//                        public void onSuccess(String responseBody, Headers headers) {
//                            stopProgressIndicator();
//                            Log.d(TAG, "Notifications request success");
//                            try {
//                                JSONArray jsonArray = new JSONArray(responseBody);
//                                Log.d(TAG, jsonArray.toString());
//                                mGeofences.clear();
//                                for (int i = 0; i < jsonArray.length(); i++) {
//                                    try {
//                                        JSONObject sitemapJson = jsonArray.getJSONObject(i);
//                                        mGeofences.add(OpenHABNotification.fromJson(sitemapJson));
//                                    } catch (JSONException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                                mGeofenceAdapter.notifyDataSetChanged();
//                            } catch(JSONException e) {
//                                Log.d(TAG, e.getMessage(), e);
//                            }
//                        }
//
//                        @Override
//                        public void onFailure(Request request, int statusCode, Throwable error) {
//                            stopProgressIndicator();
//                            Log.e(TAG, "Notifications request failure");
//                        }
//                    });
    }

    private void stopProgressIndicator() {
        if (mActivity != null) {
            //Log.d(TAG, "Stop progress indicator");
            mActivity.setProgressIndicatorVisible(false);
        }
    }

    private void startProgressIndicator() {
        if (mActivity != null) {
            //Log.d(TAG, "Start progress indicator");
            mActivity.setProgressIndicatorVisible(true);
        }
        mSwipeLayout.setRefreshing(false);
    }
}
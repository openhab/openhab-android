package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.GeofencingService;
import org.openhab.habdroid.model.OpenHABGeofence;
import org.openhab.habdroid.ui.widget.DividerItemDecoration;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            FragmentManager fm = getActivity().getSupportFragmentManager();
            NewGeofenceDialogFragment newGeofenceDialogFragment = NewGeofenceDialogFragment.newInstance(null);
            newGeofenceDialogFragment.show(fm, getResources().getString(R.string.geofence_dialog_new));
            //GeofencingService.addGeofence(getActivity(),new OpenHABGeofence(42,42,"42","fortytwo"+i++));
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

    private void loadGeofences() {
        mGeofences.clear();
        mGeofences.addAll(GeofencingService.getGeofences(getActivity()));
        //mGeofences.add(new OpenHABGeofence(30.897957, -77.036560,100,"White House"));
        mGeofenceAdapter.notifyDataSetChanged();


    }

    // This is called when the dialog is completed and the results have been passed
    public static class RegexInputFilter implements InputFilter {
        private Pattern mPattern;
        private static final String CLASS_NAME = RegexInputFilter.class.getSimpleName();
        /**
         * Convenience constructor, builds Pattern object from a String
         * @param pattern Regex string to build pattern from.
         */
        public RegexInputFilter(String pattern) {
            this(Pattern.compile(pattern));
        }
        public RegexInputFilter(Pattern pattern) {
            if (pattern == null) {
                throw new IllegalArgumentException(CLASS_NAME + " requires a regex.");
            }

            mPattern = pattern;
        }
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {

            Matcher matcher = mPattern.matcher(source);
            if (!matcher.matches()) {
                return "";
            }

            return null;
        }
    }
    public static class NewGeofenceDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {



            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();
            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            View view = inflater.inflate(R.layout.openhabgeofences_dialog, null);
            EditText label = view.findViewById(R.id.geofencelabel);
            EditText name = view.findViewById(R.id.geofencename);
            name.setFilters(new InputFilter[]{
                    new RegexInputFilter("[A-Za-z_\\-0-9]*")});
            EditText radius = view.findViewById(R.id.geofenceradius);

            if (savedInstanceState != null) {
                label.setText(savedInstanceState.getCharSequence("label",""));
                name.setText(savedInstanceState.getCharSequence("name",""));
                radius.setText(savedInstanceState.getCharSequence("radius","100"));
            }
            builder.setView(view)
                    // Add action buttons
                    .setPositiveButton(R.string.geofence_dialog_create, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            OpenHABGeofence geo = null;
                            try {
                                geo = new OpenHABGeofence(
                                        42,
                                        42,
                                        Float.valueOf(radius.getText().toString()),
                                        name.getText().toString(),
                                        label.getText().toString());
                            } catch (NumberFormatException e) {
                            }
                            if (geo != null)
                                GeofencingService.addGeofence(getActivity(),geo);
                        }
                    })
                    .setNegativeButton(R.string.geofence_dialog_cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            NewGeofenceDialogFragment.this.getDialog().cancel();
                        }
                    });
            return builder.create();
        }

        public static NewGeofenceDialogFragment newInstance(Bundle args) {
            NewGeofenceDialogFragment frag = new NewGeofenceDialogFragment();

            frag.setArguments(args);
            return frag;
        }
    }
}
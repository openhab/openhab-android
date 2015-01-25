package org.openhab.habdroid.core;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import java.util.HashMap;

/**
 * Created by belovictor on 26/01/15.
 */

public class HABDroid extends Application {

    public enum TrackerName {
        APP_TRACKER // Tracker used only in this app.
    }
    private HashMap<TrackerName, Tracker> mTrackers = new HashMap<TrackerName, Tracker>();
    private static final String PROPERTY_ID = "UA-39285202-1";
    private static final String TAG = "HABDroid";

    public synchronized Tracker getTracker(TrackerName trackerId) {
        if (!mTrackers.containsKey(trackerId)) {
            Log.d(TAG, "GoogleAnalytics.getInstance");
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            analytics.setDryRun(false);
            Log.d(TAG, "newTracker");
            Tracker t = analytics.newTracker(PROPERTY_ID);
//            Tracker t = (trackerId == TrackerName.APP_TRACKER) ? analytics.newTracker(PROPERTY_ID)
//                    : (trackerId == TrackerName.GLOBAL_TRACKER) ? analytics.newTracker(R.xml.global_tracker)
//                    : analytics.newTracker(R.xml.ecommerce_tracker);
            Log.d(TAG, "Tracker done");
            mTrackers.put(trackerId, t);

        }
        return mTrackers.get(trackerId);
    }

}

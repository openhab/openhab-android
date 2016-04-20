/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.openhab.habdroid.ui.OpenHABMainActivity;

import java.io.IOException;

public class NotificationDeletedBroadcastReceiver extends BroadcastReceiver {
    private final static String TAG = NotificationDeletedBroadcastReceiver.class.getSimpleName();
    private GoogleCloudMessaging gcm;
    private Bundle sendBundle;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive()");
        if (intent.hasExtra("notificationId")) {
            gcm = GoogleCloudMessaging.getInstance(context);
            sendBundle = new Bundle();
            sendBundle.putString("type", "hideNotification");
            sendBundle.putString("notificationId", String.valueOf(intent.getExtras().getInt("notificationId")));
            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... params) {
                    try {
                        gcm.send(OpenHABMainActivity.GCM_SENDER_ID + "@gcm.googleapis.com",
                                "1", sendBundle);
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    return null;
                }
            }.execute(null, null, null);
//            context.unregisterReceiver(this);
        }
    }
}

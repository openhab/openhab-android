/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class GcmIntentService extends IntentService {
    private static final String TAG = GcmIntentService.class.getSimpleName();

    public static final String EXTRA_MSG = "message";
    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    public static final String ACTION_NOTIFICATION_SELECTED = "org.openhab.notification.selected";
    public static final String ACTION_NOTIFICATION_DELETED = "org.openhab.notification.deleted";

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.e(TAG, "Running foss build: don't handle intent");
    }
}

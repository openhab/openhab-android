/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.core;

import android.content.Context;
import android.content.Intent;

import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;

public class CloudMessagingHelper {
    public static void onConnectionUpdated(Context context, CloudConnection connection) {
        if (connection != null) {
            Intent intent = new Intent(context, GcmRegistrationService.class)
                    .setAction(GcmRegistrationService.ACTION_REGISTER);
            context.startService(intent);
        }
    }

    public static void onNotificationSelected(Context context, Intent intent) {
        int notificationId = intent.getIntExtra(GcmListenerService.EXTRA_NOTIFICATION_ID, -1);
        if (notificationId >= 0) {
            Intent serviceIntent = new Intent(context, GcmRegistrationService.class)
                    .setAction(GcmRegistrationService.ACTION_HIDE_NOTIFICATION)
                    .putExtra(GcmRegistrationService.EXTRA_NOTIFICATION_ID, notificationId);
            context.startService(serviceIntent);
        }
    }
}

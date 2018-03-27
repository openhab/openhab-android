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
import android.support.annotation.StringRes;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.AbstractConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.GcmCloudConnection;

public class CloudMessagingHelper {
    public static Connection createConnection(AbstractConnection remoteConnection) {
        return GcmCloudConnection.fromConnection(remoteConnection);
    }

    public static void onCloudConnectionUpdated(Context context, Connection cloudConnection) {
        if (cloudConnection != null) {
            Intent intent = new Intent(context, GcmRegistrationService.class)
                    .setAction(GcmRegistrationService.ACTION_REGISTER);
            context.startService(intent);
        }
    }

    public static void onNotificationSelected(Context context, Intent intent) {
        int notificationId = intent.getIntExtra(GcmMessageListenerService.EXTRA_NOTIFICATION_ID, -1);
        if (notificationId >= 0) {
            Intent serviceIntent = new Intent(context, GcmRegistrationService.class)
                    .setAction(GcmRegistrationService.ACTION_HIDE_NOTIFICATION)
                    .putExtra(GcmRegistrationService.EXTRA_NOTIFICATION_ID, notificationId);
            context.startService(serviceIntent);
        }
    }

    public static @StringRes int getPushNotificationStatusResId() {
        GcmCloudConnection cloudConnection = (GcmCloudConnection)
                ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (cloudConnection == null) {
            if (ConnectionFactory.getConnection(Connection.TYPE_REMOTE) == null) {
                return R.string.info_openhab_gcm_no_remote;
            } else {
                return R.string.info_openhab_gcm_unsupported;
            }
        } else if (!cloudConnection.registrationDone()) {
            return R.string.info_openhab_gcm_in_progress;
        } else if (!cloudConnection.registrationSuccessful()) {
            return R.string.info_openhab_gcm_failed;
        } else {
            return R.string.info_openhab_gcm_connected;
        }
    }
}

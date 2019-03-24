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
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;

public class CloudMessagingHelper {
    static boolean sRegistrationDone;
    static Throwable sRegistrationFailureReason;

    public static void onConnectionUpdated(Context context, CloudConnection connection) {
        sRegistrationDone = false;
        if (connection != null) {
            FcmRegistrationService.scheduleRegistration(context);
        }
    }

    public static void onNotificationSelected(Context context, Intent intent) {
        int notificationId = intent.getIntExtra(
                FcmMessageListenerService.EXTRA_NOTIFICATION_ID, -1);
        if (notificationId >= 0) {
            FcmRegistrationService.scheduleHideNotification(context, notificationId);
        }
    }

    public static @StringRes int getPushNotificationStatusResId() {
        CloudConnection cloudConnection = (CloudConnection)
                ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (cloudConnection == null) {
            if (ConnectionFactory.getConnection(Connection.TYPE_REMOTE) == null) {
                return R.string.info_openhab_gcm_no_remote;
            } else {
                return R.string.info_openhab_gcm_unsupported;
            }
        } else if (!sRegistrationDone) {
            return R.string.info_openhab_gcm_in_progress;
        } else if (sRegistrationFailureReason != null) {
            return R.string.info_openhab_gcm_failed;
        } else {
            return R.string.info_openhab_gcm_connected;
        }
    }

    public static @DrawableRes int getPushNotificationIconResId() {
        CloudConnection cloudConnection = (CloudConnection)
                ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
        if (cloudConnection == null) {
            return R.drawable.ic_bell_off_outline_grey_24dp;
        } else if (!sRegistrationDone) {
            return R.drawable.ic_bell_outline_grey_24dp;
        } else if (sRegistrationFailureReason != null) {
            return R.drawable.ic_bell_off_outline_grey_24dp;
        } else {
            return R.drawable.ic_bell_ring_outline_grey_24dp;
        }
    }

    public static boolean isSupported() {
        return true;
    }
}

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

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.CloudConnection;

public class CloudMessagingHelper {
    public static void onConnectionUpdated(Context context, CloudConnection connection) {
    }

    public static void onNotificationSelected(Context context, Intent intent) {
    }

    public static String getPushNotificationStatusResId(Context context) {
        return context.getString(R.string.info_openhab_notification_status_unavailable);
    }

    public static @DrawableRes int getPushNotificationIconResId() {
        return R.drawable.ic_bell_off_outline_grey_24dp;
    }

    public static boolean isSupported() {
        return false;
    }
}

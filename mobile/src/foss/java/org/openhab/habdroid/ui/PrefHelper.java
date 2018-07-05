/*
 * Copyright (c) 2010-2018, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available
 * at https://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.preference.Preference;
import android.preference.PreferenceScreen;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;

public class PrefHelper {
    public static void removeUnsupportedPrefs(PreferenceScreen ps) {
        Preference ringtonePreference = ps.findPreference(Constants.PREFERENCE_TONE);
        ringtonePreference.setEnabled(false);
        ringtonePreference.setSummary(R.string.info_openhab_notification_status_unavailable);
        Preference vibrationPreference =
                ps.findPreference(Constants.PREFERENCE_NOTIFICATION_VIBRATION);
        vibrationPreference.setEnabled(false);
        vibrationPreference.setSummary(R.string.info_openhab_notification_status_unavailable);
    }
}

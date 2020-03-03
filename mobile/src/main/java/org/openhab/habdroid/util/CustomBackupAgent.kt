/*
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.util

import android.app.backup.BackupAgentHelper
import android.util.Log
import androidx.core.content.edit

// Used in manifest
@Suppress("unused")
class CustomBackupAgent : BackupAgentHelper() {
    override fun onRestoreFinished() {
        super.onRestoreFinished()
        Log.d(TAG, "Restore finished")
        getPrefs().edit {
            putBoolean(PrefKeys.RECENTLY_RESTORED, true)
        }
    }

    companion object {
        private val TAG = CustomBackupAgent::class.java.simpleName
    }
}

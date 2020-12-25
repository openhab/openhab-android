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

import android.util.Log

object RemoteLog {
    fun initialize() {
        // no-op
    }

    fun d(tag: String, message: String, remoteOnly: Boolean = false) {
        if (!remoteOnly) {
            Log.d(tag, message)
        }
    }

    fun e(tag: String, message: String, remoteOnly: Boolean = false) {
        if (!remoteOnly) {
            Log.e(tag, message)
        }
    }

    fun nonFatal(@Suppress("UNUSED_PARAMETER") e: Throwable) {
        // no-op
    }
}

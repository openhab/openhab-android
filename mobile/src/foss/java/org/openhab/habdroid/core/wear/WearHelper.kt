/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

package org.openhab.habdroid.core.wear

import android.content.Context

/**
 * Wear OS requires Google Play services, so the config isn't published in the FOSS version
 */
object WearHelper {
    const val IS_SUPPORTED = false

    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context) {}

    @Suppress("UNUSED_PARAMETER")
    suspend fun hasWatchWithApp(context: Context): Boolean = false
}

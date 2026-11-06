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

package org.openhab.habdroid.wear.complication

import android.content.Context
import androidx.core.content.edit

/**
 * Stores which item is shown by which complication
 */
class ComplicationConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("complications", Context.MODE_PRIVATE)

    fun getItemName(complicationId: Int): String? = prefs.getString(key(complicationId), null)

    fun setItemName(complicationId: Int, itemName: String) = prefs.edit { putString(key(complicationId), itemName) }

    fun remove(complicationId: Int) = prefs.edit { remove(key(complicationId)) }

    private fun key(complicationId: Int) = "item_$complicationId"
}

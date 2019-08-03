/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

package org.openhab.habdroid.ui.homescreenwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log

open class ItemUpdateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate()")
    }

    override fun onEnabled(context: Context?) {
        Log.d(TAG, "onEnabled()")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive()")
    }

    companion object {
        private val TAG = ItemUpdateWidget::class.java.simpleName
    }
}

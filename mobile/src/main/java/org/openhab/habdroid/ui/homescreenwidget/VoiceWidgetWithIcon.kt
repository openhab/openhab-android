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

package org.openhab.habdroid.ui.homescreenwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.LayoutRes

import org.openhab.habdroid.R
import org.openhab.habdroid.ui.MainActivity

class VoiceWidgetWithIcon : VoiceWidget() {
    override val layoutRes: Int @LayoutRes get() = R.layout.widget_voice_with_icon

    override fun setupOpenhabIcon(context: Context, views: RemoteViews) {
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(context, 8, mainIntent, 0)
        views.setOnClickPendingIntent(R.id.btn_open_main, mainPendingIntent)
    }
}

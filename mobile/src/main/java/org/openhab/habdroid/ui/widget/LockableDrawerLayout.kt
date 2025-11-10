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

package org.openhab.habdroid.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.drawerlayout.widget.DrawerLayout

class LockableDrawerLayout(context: Context, attrs: AttributeSet?) : DrawerLayout(context, attrs) {
    var swipeToOpenDisabled = false
    private val shouldIgnoreTouch
        get() = swipeToOpenDisabled && !isOpen

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (shouldIgnoreTouch) {
            return false
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (shouldIgnoreTouch) {
            return false
        }
        return super.onTouchEvent(ev)
    }
}

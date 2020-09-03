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

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.widget.AppCompatImageButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PeriodicSignalImageButton constructor(context: Context, attrs: AttributeSet?) :
    AppCompatImageButton(context, attrs),
    View.OnLongClickListener,
    View.OnTouchListener {
    private var scope: CoroutineScope? = null
    private var periodicCallbackExecutor: Job? = null

    var callback: ((v: View, value: String?) -> Unit)? = null
    var clickCommand: String? = null
    var longClickHoldCommand: String? = null

    init {
        setOnLongClickListener(this)
        setOnTouchListener(this)
    }

    @CallSuper
    override fun onLongClick(v: View?): Boolean {
        scheduleNextSignal()
        return true
    }

    @CallSuper
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (periodicCallbackExecutor == null) {
                callback?.invoke(this@PeriodicSignalImageButton, clickCommand)
            } else {
                periodicCallbackExecutor?.cancel()
                periodicCallbackExecutor = null
            }
        }
        return false
    }

    private fun scheduleNextSignal() {
        periodicCallbackExecutor = scope?.launch {
            delay(250)
            callback?.invoke(this@PeriodicSignalImageButton, longClickHoldCommand)
            scheduleNextSignal()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + Job())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.cancel()
        scope = null
    }
}

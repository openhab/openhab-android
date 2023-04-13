/*
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import android.util.Log
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.util.beautify

class WidgetSlider constructor(context: Context, attrs: AttributeSet?) :
    Slider(context, attrs),
    LabelFormatter,
    Slider.OnChangeListener,
    Slider.OnSliderTouchListener {

    interface UpdateListener {
        suspend fun onValueUpdate(value: Float)
    }

    var updateListener: UpdateListener? = null
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null
    private var updateOnMove = true
    private var boundItem: Item? = null

    init {
        addOnChangeListener(this)
        addOnSliderTouchListener(this)
        setLabelFormatter(this)
    }

    fun bindToWidget(widget: Widget, updateOnMove: Boolean) {
        val isColor = widget.item?.isOfTypeOrGroupType(Item.Type.Color) == true
        val from = if (isColor) 0F else widget.minValue
        val to = if (isColor) 100F else widget.maxValue
        val step = if (isColor) 1F else widget.step
        val state = widget.item?.state
        val widgetValue = (if (isColor) state?.asBrightness?.toFloat() else state?.asNumber?.value) ?: from

        updateJob?.cancel()
        this.updateOnMove = updateOnMove
        this.boundItem = widget.item

        // Fix "The stepSize must be 0, or a factor of the valueFrom-valueTo range" exception
        valueTo = to - (to - from).rem(step)
        valueFrom = from
        stepSize = step

        // Fix "Value must be equal to valueFrom plus a multiple of stepSize when using stepSize"
        val stepCount = (abs(valueTo - valueFrom) / stepSize).toInt()
        var closestValue = valueFrom
        var closestDelta = Float.MAX_VALUE
        (0..stepCount).map { index ->
            val stepValue = valueFrom + index * stepSize
            if (abs(widgetValue - stepValue) < closestDelta) {
                closestValue = stepValue
                closestDelta = abs(widgetValue - stepValue)
            }
        }

        Log.d(
            TAG,
            "Slider: valueFrom = $valueFrom, valueTo = $valueTo, " +
                "stepSize = $stepSize, stepCount = $stepCount, widgetValue = $widgetValue, " +
                "closestValue = $closestValue, closestDelta = $closestDelta"
        )

        isTickVisible = stepCount <= 12
        value = closestValue
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + Job())
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun onStartTrackingTouch(slider: Slider) {
        // not implemented
    }

    override fun onStopTrackingTouch(slider: Slider) {
        if (!updateOnMove) {
            updateJob?.cancel()
            updateJob = scope?.launch {
                updateListener?.onValueUpdate(value)
            }
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser && updateOnMove) {
            updateJob?.cancel()
            updateJob = scope?.launch {
                delay(200)
                updateListener?.onValueUpdate(value)
            }
        }
    }

    override fun getFormattedValue(value: Float): String {
        val item = boundItem ?: return ""
        return if (item.isOfTypeOrGroupType(Item.Type.Color)) {
            "${value.beautify()} %"
        } else {
            item.state?.asNumber.withValue(value).toString()
        }
    }

    companion object {
        private val TAG = WidgetSlider::class.java.simpleName
    }
}


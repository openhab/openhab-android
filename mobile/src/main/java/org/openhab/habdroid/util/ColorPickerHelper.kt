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

package org.openhab.habdroid.util

import android.graphics.Color
import android.util.Log
import com.chimbori.colorpicker.ColorPickerView
import com.chimbori.colorpicker.OnColorChangedListener
import com.chimbori.colorpicker.OnColorSelectedListener
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.ui.sendItemCommand

class ColorPickerHelper(private val colorPicker: ColorPickerView, private val slider: Slider) :
    Slider.OnChangeListener,
    OnColorChangedListener,
    OnColorSelectedListener,
    LabelFormatter {
    private var boundItem: Item? = null
    private var scope: CoroutineScope? = null
    private var lastUpdate: Job? = null
    private var connection: Connection? = null

    init {
        colorPicker.addOnColorChangedListener(this)
        colorPicker.addOnColorSelectedListener(this)
        slider.addOnChangeListener(this)
        slider.setLabelFormatter(this)
    }

    fun attach(item: Item?, scope: CoroutineScope, connection: Connection?) {
        item?.state?.asHsv?.toColor(false)?.let { colorPicker.setColor(it, true) }
        item?.state?.asBrightness?.let { slider.value = it.toFloat() }

        this.boundItem = item
        this.scope = scope
        this.connection = connection
    }

    fun detach() {
        lastUpdate?.cancel()
        scope = null
        boundItem = null
        connection = null
    }

    override fun onColorChanged(selectedColor: Int) {
        Log.d(TAG, "onColorChanged($selectedColor)")
        setBrightnessIfNeeded()
        handleChange(false)
    }

    override fun onColorSelected(selectedColor: Int) {
        Log.d(TAG, "onColorSelected($selectedColor)")
        setBrightnessIfNeeded()
        handleChange(true)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            handleChange(false)
        }
    }

    override fun getFormattedValue(value: Float): String = "${value.toInt()} %"

    private fun setBrightnessIfNeeded() {
        if (slider.value.toInt() == 0) {
            slider.value = 100F
        }
    }

    private fun handleChange(immediate: Boolean) {
        val newColor = colorPicker.selectedColor
        val brightness = slider.value.toInt()
        Log.d(TAG, "handleChange(newColor = $newColor, brightness = $brightness, immediate = $immediate)")
        lastUpdate?.cancel()
        lastUpdate = scope?.launch {
            if (!immediate) {
                delay(200)
            }

            val hsv = FloatArray(3)
            Color.RGBToHSV(Color.red(newColor), Color.green(newColor), Color.blue(newColor), hsv)
            val newColorValue = String.format(Locale.US, "%.0f,%.0f,%.0f", hsv[0], hsv[1] * 100, brightness.toFloat())
            connection?.httpClient?.sendItemCommand(boundItem, newColorValue)
        }
    }

    companion object {
        private val TAG = ColorPickerHelper::class.java.simpleName
    }
}

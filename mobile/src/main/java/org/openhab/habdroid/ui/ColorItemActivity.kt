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

package org.openhab.habdroid.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

class ColorItemActivity :
    AbstractBaseActivity(),
    OnColorChangedListener,
    Slider.OnChangeListener,
    LabelFormatter,
    OnColorSelectedListener {
    private var boundItem: Item? = null
    private var slider: Slider? = null
    private var colorPicker: ColorPickerView? = null
    private var lastUpdate: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_color_picker)

        boundItem = intent.extras?.parcelable(EXTRA_ITEM)

        supportActionBar?.title = boundItem?.label.orDefaultIfEmpty(getString(R.string.widget_type_color))

        colorPicker = findViewById<ColorPickerView>(R.id.picker).apply {
            boundItem?.state?.asHsv?.toColor(false)?.let { setColor(it, true) }

            addOnColorChangedListener(this@ColorItemActivity)
            addOnColorSelectedListener(this@ColorItemActivity)
        }

        slider = findViewById<Slider>(R.id.brightness_slider).apply {
            boundItem?.state?.asBrightness?.let { value = it.toFloat() }

            addOnChangeListener(this@ColorItemActivity)
            setLabelFormatter(this@ColorItemActivity)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onColorChanged(selectedColor: Int) {
        Log.d(WidgetAdapter.TAG, "onColorChanged($selectedColor)")
        setBrightnessIfNeeded()
        handleChange(false)
    }

    override fun onColorSelected(selectedColor: Int) {
        Log.d(WidgetAdapter.TAG, "onColorSelected($selectedColor)")
        setBrightnessIfNeeded()
        handleChange(true)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            handleChange(false)
        }
    }

    override fun getFormattedValue(value: Float): String {
        return "${value.toInt()} %"
    }

    private fun setBrightnessIfNeeded() {
        if (slider?.value?.toInt() == 0) {
            slider?.value = 100F
        }
    }

    private fun handleChange(immediate:  Boolean) {
        val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return
        val newColor = colorPicker?.selectedColor ?: return
        val brightness = slider?.value?.toInt() ?: 0
        Log.d(TAG, "handleChange(newColor = $newColor, brightness = $brightness, immediate = $immediate)")
        lastUpdate?.cancel()
        lastUpdate = launch {
            if (!immediate) {
                delay(200)
            }

            val hsv = FloatArray(3)
            Color.RGBToHSV(Color.red(newColor), Color.green(newColor), Color.blue(newColor), hsv)
            val newColorValue = String.format(Locale.US, "%.0f,%.0f,%.0f", hsv[0], hsv[1] * 100, brightness.toFloat())
            connection.httpClient.sendItemCommand(boundItem, newColorValue)
        }
    }

    companion object {
        private val TAG = ColorItemActivity::class.java.simpleName

        const val EXTRA_ITEM = "item"
    }
}

/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MenuItem
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.Job
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

class ColorItemActivity :
    AbstractBaseActivity(),
    OnColorChangedListener,
    Slider.OnSliderTouchListener,
    Slider.OnChangeListener,
    LabelFormatter,
    OnColorSelectedListener,
    Handler.Callback {
    private var boundItem: Item? = null
    private val handler = Handler(Looper.getMainLooper(), this)
    private var slider: Slider? = null
    private var colorPicker: ColorPickerView? = null
    private var lastUpdate: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_color_picker)

        boundItem = intent.extras?.parcelable(EXTRA_ITEM)

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
            addOnSliderTouchListener(this@ColorItemActivity)
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
        handleChange(true)
    }

    override fun onStartTrackingTouch(slider: Slider) {
        // no-op
    }

    override fun onStopTrackingTouch(slider: Slider) {
        handleChange(false, 0)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            handleChange(false)
        }
    }

    override fun getFormattedValue(value: Float): String {
        return "${value.toInt()} %"
    }

    override fun onColorSelected(selectedColor: Int) {
        Log.d(WidgetAdapter.TAG, "onColorSelected($selectedColor)")
        handleChange(true, 0)
    }

    private fun handleChange(colorChanged: Boolean, delay: Long = 100) {
        val newColor = colorPicker?.selectedColor ?: return
        var brightness = slider?.value?.toInt() ?: 0
        Log.d(WidgetAdapter.TAG, "handleChange(newColor = $newColor, brightness = $brightness, delay = $delay)")
        if (colorChanged && brightness == 0) {
            brightness = 100
            slider?.value = 100F
        }
        handler.removeMessages(0)
        handler.sendMessageDelayed(handler.obtainMessage(0, newColor, brightness), delay)
    }

    override fun handleMessage(msg: Message): Boolean {
        val hsv = FloatArray(3)
        Color.RGBToHSV(Color.red(msg.arg1), Color.green(msg.arg1), Color.blue(msg.arg1), hsv)
        hsv[2] = msg.arg2.toFloat()
        Log.d(WidgetAdapter.TAG, "New color HSV = ${hsv[0]}, ${hsv[1]}, ${hsv[2]}")
        val newColorValue = String.format(Locale.US, "%f,%f,%f", hsv[0], hsv[1] * 100, hsv[2])
        lastUpdate?.cancel()
        val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return false
        lastUpdate = connection.httpClient.sendItemCommand(boundItem, newColorValue)
        return true
    }

    companion object {
        private val TAG = ColorItemActivity::class.java.simpleName

        const val EXTRA_ITEM = "item"
    }
}

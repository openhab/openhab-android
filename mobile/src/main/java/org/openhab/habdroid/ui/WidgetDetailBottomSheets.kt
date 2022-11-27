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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.Job
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.util.parcelable

open class AbstractWidgetDetailBottomSheet : BottomSheetDialogFragment() {
    protected val widget get() = requireArguments().parcelable<Widget>("widget")!!
    protected val connection get() = (parentFragment as ConnectionGetter).getConnection()

    companion object {
        fun createArguments(widget: Widget): Bundle {
            return bundleOf("widget" to widget)
        }
    }

    interface ConnectionGetter {
        fun getConnection(): Connection?
    }
}

class SetpointBottomSheet : AbstractWidgetDetailBottomSheet(), Slider.OnChangeListener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_setpoint, container, false)
        val state = widget.state?.asNumber

        view.findViewById<Slider>(R.id.slider).apply {
            valueFrom = widget.minValue
            valueTo = widget.maxValue
            value = state?.value ?: widget.minValue
            stepSize = if (widget.minValue == widget.maxValue) 1F else widget.step
            labelBehavior = LabelFormatter.LABEL_VISIBLE
            setLabelFormatter { value -> state.withValue(value).toString() }
            addOnChangeListener(this@SetpointBottomSheet)
        }

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }

        return view
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        val state = widget.state?.asNumber.withValue(value)
        connection?.httpClient?.sendItemUpdate(widget.item, state)
    }
}

class SelectionBottomSheet : AbstractWidgetDetailBottomSheet(), RadioGroup.OnCheckedChangeListener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_selection, container, false)
        val group = view.findViewById<RadioGroup>(R.id.group)
        val stateString = widget.item?.state?.asString
        for (mapping in widget.mappingsOrItemOptions) {
            val radio = inflater.inflate(R.layout.bottom_sheet_selection_item_radio_button, group, false) as RadioButton
            radio.id = mapping.hashCode()
            radio.text = mapping.label
            radio.isChecked = stateString == mapping.value
            group.addView(radio)
        }
        group.setOnCheckedChangeListener(this)

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }
        return view
    }

    override fun onCheckedChanged(group: RadioGroup?, id: Int) {
        val mapping = widget.mappingsOrItemOptions.firstOrNull { mapping -> mapping.hashCode() == id }
        if (mapping != null) {
            connection?.httpClient?.sendItemCommand(widget.item, mapping.value)
        }
        dismissAllowingStateLoss()
    }
}

class ColorChooserBottomSheet : AbstractWidgetDetailBottomSheet(), Handler.Callback, OnColorChangedListener,
    OnColorSelectedListener, Slider.OnChangeListener, Slider.OnSliderTouchListener {
    companion object {
        private val TAG = "ColorChooserBottomSheet"
    }

    private lateinit var colorPicker: ColorPickerView
    private lateinit var slider: Slider
    private val handler = Handler(Looper.getMainLooper(), this)
    private var lastUpdate: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_color_picker, container, false)

        colorPicker = view.findViewById<ColorPickerView>(R.id.picker).apply {
            widget.item?.state?.asHsv?.toColor(false)?.let { setColor(it, true) }

            addOnColorChangedListener(this@ColorChooserBottomSheet)
            addOnColorSelectedListener(this@ColorChooserBottomSheet)
        }

        slider = view.findViewById<Slider>(R.id.brightness_slider).apply {
            widget.item?.state?.asBrightness?.let { value = it.toFloat() }
            setLabelFormatter { value -> "${value.toInt()} %" }

            addOnChangeListener(this@ColorChooserBottomSheet)
            addOnSliderTouchListener(this@ColorChooserBottomSheet)
        }

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }

        return view
    }

    override fun onColorSelected(selectedColor: Int) {
        Log.d(TAG, "onColorSelected($selectedColor)")
        handleChange(true, 0)
    }

    override fun onColorChanged(selectedColor: Int) {
        Log.d(TAG, "onColorChanged($selectedColor)")
        handleChange(true)
    }

    // Brightness slider
    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            handleChange(false)
        }
    }

    override fun onStartTrackingTouch(slider: Slider) {
        // no-op
    }

    override fun onStopTrackingTouch(slider: Slider) {
        handleChange(false, 0)
    }

    private fun handleChange(colorChanged: Boolean, delay: Long = 100) {
        val newColor = colorPicker.selectedColor
        var brightness = slider.value.toInt()
        Log.d(TAG, "handleChange(newColor = $newColor, brightness = $brightness, delay = $delay)")
        if (colorChanged && brightness == 0) {
            brightness = 100
            slider.value = 100F
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
        lastUpdate = connection?.httpClient?.sendItemCommand(widget.item, newColorValue)
        return true
    }
}

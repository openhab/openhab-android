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

package org.openhab.habdroid.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.chimbori.colorpicker.ColorPickerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.toColorTemperatureInKelvin
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.ui.widget.WidgetSlider
import org.openhab.habdroid.util.ColorPickerHelper
import org.openhab.habdroid.util.asColorTemperatureInKelvinToColor
import org.openhab.habdroid.util.asColorTemperatureToKelvin
import org.openhab.habdroid.util.parcelable

open class AbstractWidgetBottomSheet : BottomSheetDialogFragment() {
    protected val widget get() = requireArguments().parcelable<Widget>("widget")!!
    protected val connection get() = (parentFragment as ConnectionGetter).getConnection()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(R.id.design_bottom_sheet) as FrameLayout
            val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetBehavior.peekHeight = bottomSheet.height
        }
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        fun createArguments(widget: Widget): Bundle {
            return bundleOf("widget" to widget)
        }
    }

    interface ConnectionGetter {
        fun getConnection(): Connection?
    }
}

open class SliderBottomSheet : AbstractWidgetBottomSheet(), WidgetSlider.UpdateListener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_setpoint, container, false)

        view.findViewById<WidgetSlider>(R.id.slider).apply {
            labelBehavior = LabelFormatter.LABEL_VISIBLE
            updateListener = this@SliderBottomSheet
            bindToWidget(widget, false)
        }

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }

        return view
    }

    override suspend fun onValueUpdate(value: Float) {
        val item = widget.item ?: return
        val state = widget.state?.asNumber.withValue(value)
        Log.d(TAG, "Send state $state for ${item.name}")
        connection?.httpClient?.sendItemUpdate(item, state)
    }

    companion object {
        private val TAG = SliderBottomSheet::class.java.simpleName
    }
}

class ColorTemperatureSliderBottomSheet : SliderBottomSheet(), View.OnLayoutChangeListener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = super.onCreateView(inflater, container, savedInstanceState)
        v.findViewById<WidgetSlider>(R.id.slider).apply {
            addOnLayoutChangeListener(this@ColorTemperatureSliderBottomSheet)
            setLabelFormatter { value -> "${value.roundToInt()} K" }
        }
        return v
    }

    override suspend fun onValueUpdate(value: Float) {
        val item = widget.item ?: return
        val state = widget.state?.asNumber?.toColorTemperatureInKelvin()?.withValue(value)
        Log.d(TAG, "Send state $state for ${item.name}")
        connection?.httpClient?.sendItemUpdate(item, state)
    }

    override fun onLayoutChange(view: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
        applyColorTemperatureGradientToTrack(view as WidgetSlider, r - l, b - t)
    }

    private fun applyColorTemperatureGradientToTrack(slider: WidgetSlider, width: Int, height: Int) {
        val min = widget.minValue.asColorTemperatureToKelvin()
        val max = widget.maxValue.asColorTemperatureToKelvin()
        val steps = 20
        val positions = (0 until steps).map { 1F * it / steps }.toFloatArray()
        val colors = positions
            .map { it * (max - min) + min }
            .map { it.asColorTemperatureInKelvinToColor() }
            .toIntArray()
        val shader = LinearGradient(0F, 0F, width.toFloat(), height.toFloat(), colors, positions, Shader.TileMode.CLAMP)

        listOf("activeTrackPaint", "inactiveTrackPaint")
            .map { Slider::class.java.superclass.getDeclaredField(it) }
            .forEach { field ->
                field.isAccessible = true
                (field.get(slider) as Paint).shader = shader
            }
    }

    companion object {
        private val TAG = SliderBottomSheet::class.java.simpleName
    }
}

class SelectionBottomSheet : AbstractWidgetBottomSheet() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_selection, container, false)
        val group = view.findViewById<RadioGroup>(R.id.group)
        val stateString = widget.state?.asString
        for (mapping in widget.mappingsOrItemOptions) {
            val radio = inflater.inflate(R.layout.bottom_sheet_selection_item_radio_button, group, false) as RadioButton
            radio.id = mapping.hashCode()
            radio.text = mapping.label
            radio.isChecked = stateString == mapping.value
            radio.setOnClickListener {
                connection?.httpClient?.sendItemCommand(widget.item, mapping.value)
                dismissAllowingStateLoss()
            }
            group.addView(radio)
        }

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }
        return view
    }
}

class ColorChooserBottomSheet : AbstractWidgetBottomSheet() {
    private lateinit var pickerHelper: ColorPickerHelper
    private var scope: CoroutineScope? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_color_picker, container, false)

        val colorPicker = view.findViewById<ColorPickerView>(R.id.picker)
        val slider = view.findViewById<Slider>(R.id.brightness_slider)
        pickerHelper = ColorPickerHelper(colorPicker, slider)

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        val scope = CoroutineScope(Dispatchers.Main + Job())
        pickerHelper.attach(widget.item, scope, connection)
        this.scope = scope
    }

    override fun onPause() {
        super.onPause()
        pickerHelper.detach()
        scope?.cancel()
        scope = null
    }
}

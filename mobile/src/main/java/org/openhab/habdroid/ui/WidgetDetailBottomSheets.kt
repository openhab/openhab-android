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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.Connection
import org.openhab.habdroid.model.Widget
import org.openhab.habdroid.model.withValue
import org.openhab.habdroid.ui.widget.WidgetSlider
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

class SliderBottomSheet : AbstractWidgetBottomSheet(), WidgetSlider.UpdateListener {
    private var updateJob: Job? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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

class SelectionBottomSheet : AbstractWidgetBottomSheet(), RadioGroup.OnCheckedChangeListener {
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

class ColorChooserBottomSheet :
    AbstractWidgetBottomSheet(),
    OnColorChangedListener,
    OnColorSelectedListener,
    Slider.OnChangeListener {
    companion object {
        private val TAG = "ColorChooserBottomSheet"
    }

    private lateinit var colorPicker: ColorPickerView
    private lateinit var slider: Slider
    private var lastUpdate: Job? = null
    private var scope: CoroutineScope? = null

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
        }

        view.findViewById<TextView>(R.id.title).apply {
            text = widget.label
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        scope = CoroutineScope(Dispatchers.Main + Job())
    }

    override fun onPause() {
        super.onPause()
        scope?.cancel()
        scope = null
    }

    override fun onColorSelected(selectedColor: Int) {
        Log.d(TAG, "onColorSelected($selectedColor)")
        setBrightnessIfNeeded()
        handleChange(true)
    }

    override fun onColorChanged(selectedColor: Int) {
        Log.d(TAG, "onColorChanged($selectedColor)")
        setBrightnessIfNeeded()
        handleChange(false)
    }

    // Brightness slider
    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            handleChange(false)
        }
    }

    private fun setBrightnessIfNeeded() {
        if (slider.value.toInt() == 0) {
            slider.value = 100F
        }
    }
    private fun handleChange(immediate:  Boolean) {
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
            connection?.httpClient?.sendItemCommand(widget.item, newColorValue)
        }
    }
}

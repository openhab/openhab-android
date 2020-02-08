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

package org.openhab.habdroid.util

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import com.google.android.material.slider.Slider
import org.openhab.habdroid.R
import org.openhab.habdroid.model.Item
import kotlin.math.max

class ColorPicker(val context: Context, val inflater: LayoutInflater) : Slider.LabelFormatter,
    Slider.OnSliderTouchListener {
    private var listener: OnColorPickerChangeListener? = null
    private var slider: Slider? = null

    fun showDialog(
        colors: IntArray,
        listener: OnColorPickerChangeListener,
        item: Item
    ): AlertDialog {
        val alertLayout: View = inflater.inflate(R.layout.color_picker_dialog, null)

        val gridItems = ArrayList<ColorPickerItem>()
        colors.forEach { color ->
            gridItems.add(ColorPickerItem(color))
        }

        this.listener = listener

        val colorGridAdapter = ColorPickerAdapter(context, R.layout.color_picker_item, gridItems, listener)
        val gridView: GridView = alertLayout.findViewById(R.id.grid)
        gridView.adapter = colorGridAdapter
        val resources = context.resources
        val columnCount = resources.pixelToDp(resources.displayMetrics.widthPixels).toInt() / 100
        gridView.numColumns = max(columnCount, 2)
        colorGridAdapter.notifyDataSetChanged()

        slider = alertLayout.findViewById(R.id.brightness)
        slider?.setLabelFormatter(this)
        slider?.addOnSliderTouchListener(this)
        slider?.value = item.state?.asBrightness?.toFloat() ?: 0F

        val dialog = AlertDialog.Builder(context)
            .setTitle(item.label ?: context.getString(R.string.settings_select_a_color))
            .setView(alertLayout)
            .setPositiveButton(R.string.close, null)
            .show()

        dialog?.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        return dialog
    }

    interface OnColorPickerChangeListener {
        fun onColorChange(hsv: FloatArray)
        fun onBrightnessChange(brightness: Int)
    }

    data class ColorPickerItem(@ColorInt val color: Int)

    inner class ColorPickerAdapter(
        context: Context,
        private val resource: Int,
        items: List<ColorPickerItem>,
        private val listener: OnColorPickerChangeListener
    ) : ArrayAdapter<ColorPickerItem>(context, resource, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = LayoutInflater.from(context)

            val itemView = convertView ?: inflater.inflate(resource, null)

            val ci = getItem(position)!!
            val circle = itemView.findViewById<View>(R.id.color_item)
            circle.setBackgroundColor(ci.color)
            circle.setOnClickListener {
                val hsv = FloatArray(3)
                Color.RGBToHSV(Color.red(ci.color), Color.green(ci.color), Color.blue(ci.color), hsv)
                var brightness = slider?.value
                if (brightness == null || brightness == 0F) {
                    slider?.value = 100F
                    brightness = 100F
                }
                hsv[2] = brightness
                listener.onColorChange(hsv)
            }

            return itemView
        }
    }

    override fun getFormattedValue(value: Float): String {
        return "${value.toInt()} %"
    }

    override fun onStartTrackingTouch(slider: Slider) {
        // no-op
    }

    override fun onStopTrackingTouch(slider: Slider) {
        listener?.onBrightnessChange(slider.value.toInt())
    }
}

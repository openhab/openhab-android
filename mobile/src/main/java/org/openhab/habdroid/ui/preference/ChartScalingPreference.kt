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

package org.openhab.habdroid.ui.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

import org.openhab.habdroid.R

class ChartScalingPreference constructor(context: Context, attrs: AttributeSet) :
    Preference(context, attrs), SeekBar.OnSeekBarChangeListener {
    private val entries: Array<String>
    private val values: Array<Float>
    private lateinit var seekBar: SeekBar
    private lateinit var label: TextView
    private var value: Float = 0F

    init {
        layoutResource = R.layout.chart_scaling_pref

        val res = context.resources
        entries = res.getStringArray(R.array.chartScalingEntries)
        val intValues = res.getIntArray(R.array.chartScalingValues)
        values = intValues.map { v -> v.toFloat() / 100F }.toTypedArray()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (holder == null) {
            return
        }

        seekBar = holder.itemView.findViewById(R.id.seekbar)
        seekBar.setOnSeekBarChangeListener(this)
        seekBar.max = values.size - 1
        seekBar.progress = Math.max(0, values.indexOfFirst { v -> v == value })

        label = holder.itemView.findViewById(R.id.label)
        updateLabel()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getFloat(index, 1.0f)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val defaultFloat = defaultValue as? Float ?: 1.0f
        value = getPersistedFloat(defaultFloat)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromTouch: Boolean) {
        if (!fromTouch) {
            return
        }
        val value = values[progress]
        if (callChangeListener(value)) {
            this.value = value
            updateLabel()
            if (isPersistent) {
                persistFloat(value)
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        // no-op
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        // no-op
    }

    private fun updateLabel() {
        label.text = entries[seekBar.progress]
    }
}

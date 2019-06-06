package org.openhab.habdroid.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.preference.Preference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView

import org.openhab.habdroid.R

class ChartScalingPreference constructor(context: Context, attrs: AttributeSet):
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

    override fun onCreateView(parent: ViewGroup): View {
        val view = super.onCreateView(parent)
        seekBar = view.findViewById(R.id.seekbar)
        seekBar.setOnSeekBarChangeListener(this)
        seekBar.max = values.size - 1
        seekBar.progress = Math.max(0, values.indexOfFirst { v -> v == value })

        label = view.findViewById(R.id.label)
        updateLabel()

        return view
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getFloat(index, 1.0f)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restorePersistedValue, defaultValue)
        val defaultFloat = defaultValue as? Float ?: 1.0f
        value = if (restorePersistedValue) getPersistedFloat(defaultFloat) else defaultFloat
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

    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {

    }

    private fun updateLabel() {
        label.text = entries[seekBar.progress]
    }
}

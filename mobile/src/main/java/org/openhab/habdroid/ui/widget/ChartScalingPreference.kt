package org.openhab.habdroid.ui.widget

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.preference.Preference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView

import org.openhab.habdroid.R

class ChartScalingPreference : Preference, SeekBar.OnSeekBarChangeListener {
    private lateinit var entries: Array<String>
    private lateinit var values: FloatArray
    private lateinit var seekBar: SeekBar
    private lateinit var label: TextView
    private var value: Float = 0F

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    @TargetApi(21)
    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    override fun onCreateView(parent: ViewGroup): View {
        val v = super.onCreateView(parent)
        seekBar = v.findViewById(R.id.seekbar)
        seekBar.setOnSeekBarChangeListener(this)
        seekBar.max = values.size - 1
        for (i in values.indices) {
            if (values[i] == value) {
                seekBar.progress = i
                break
            }
        }

        label = v.findViewById(R.id.label)
        updateLabel()

        return v
    }

    private fun init() {
        layoutResource = R.layout.chart_scaling_pref

        val res = context.resources
        entries = res.getStringArray(R.array.chartScalingEntries)
        val intValues = res.getIntArray(R.array.chartScalingValues)
        values = FloatArray(intValues.size)
        for (i in intValues.indices) {
            values[i] = intValues[i].toFloat() / 100f
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getFloat(index, 1.0f)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
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

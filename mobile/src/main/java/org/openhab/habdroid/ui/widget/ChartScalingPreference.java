package org.openhab.habdroid.ui.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.openhab.habdroid.R;

public class ChartScalingPreference extends Preference implements SeekBar.OnSeekBarChangeListener {
    private String[] mEntries;
    private float[] mValues;
    private SeekBar mSeekBar;
    private TextView mLabel;
    private float mValue;

    public ChartScalingPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChartScalingPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(21)
    public ChartScalingPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        mSeekBar = v.findViewById(R.id.seekbar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax(mValues.length - 1);
        for (int i = 0; i < mValues.length; i++) {
            if (mValues[i] == mValue) {
                mSeekBar.setProgress(i);
                break;
            }
        }

        mLabel = v.findViewById(R.id.label);
        updateLabel();

        return v;
    }

    private void init() {
        setLayoutResource(R.layout.chart_scaling_pref);

        Resources res = getContext().getResources();
        mEntries = res.getStringArray(R.array.chartScalingEntries);
        int[] values = res.getIntArray(R.array.chartScalingValues);
        mValues = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            mValues[i] = (float) values[i] / 100f;
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getFloat(index, 1.0f);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        super.onSetInitialValue(restorePersistedValue, defaultValue);
        float defaultFloat = defaultValue instanceof Float ? (Float) defaultValue : 1.0f;
        mValue = restorePersistedValue ? getPersistedFloat(defaultFloat) : defaultFloat;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (!fromTouch) {
            return;
        }
        float value = mValues[progress];
        if (callChangeListener(value)) {
            mValue = value;
            updateLabel();
            if (isPersistent()) {
                persistFloat(value);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void updateLabel() {
        mLabel.setText(mEntries[mSeekBar.getProgress()]);
    }
}

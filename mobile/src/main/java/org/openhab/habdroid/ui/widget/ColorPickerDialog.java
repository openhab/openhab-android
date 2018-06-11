/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialog;
import android.view.View;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

import org.openhab.habdroid.R;


public class ColorPickerDialog extends AppCompatDialog implements ColorPicker.OnColorSelectedListener {
    public interface OnColorChangedListener {
        void onColorChanged(float[] hsv);
    };

    private final OnColorChangedListener mListener;
    private float[] mInitialColor;
    private ColorPicker mColorPickerView;
    private Object tag;


    public ColorPickerDialog(Context context,
                             OnColorChangedListener listener,
                             float[] initialColor) {
        super(context);

        mListener = listener;
        mInitialColor = initialColor;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View contentView = getLayoutInflater().inflate(R.layout.color_picker_dialog, null);

        SaturationBar saturationBar = contentView.findViewById(R.id.saturation_bar);
        ValueBar valueBar = contentView.findViewById(R.id.value_bar);

        mColorPickerView = contentView.findViewById(R.id.picker);
        mColorPickerView.addSaturationBar(saturationBar);
        mColorPickerView.addValueBar(valueBar);
        mColorPickerView.setOnColorSelectedListener(this);
        mColorPickerView.setShowOldCenterColor(false);
        if (mInitialColor != null) {
            mColorPickerView.setColor(Color.HSVToColor(mInitialColor));
        }

        setContentView(contentView);
    }

    @Override
    public void onColorSelected(int color) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
        mListener.onColorChanged(hsv);
    }
}

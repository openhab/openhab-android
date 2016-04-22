/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class ColorPickerDialog extends Dialog {

	private static final String TAG = ColorPickerDialog.class.getSimpleName();
    private OnColorChangedListener mListener;
    private float[] mInitialColor;
    private ColorPicker colorPickerView;
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
        OnColorChangedListener l = new OnColorChangedListener() {
            public void colorChanged(float[] color, View v) {
                mListener.colorChanged(color, v);
                Log.d(TAG, String.format("New color = %f %f %f", color[0], color[1], color[2]));
//                dismiss();
            }
        };
        // TODO: add initial color
        this.colorPickerView = new ColorPicker(getContext(), l);
        this.colorPickerView.setHSVColor(mInitialColor);
        if (this.tag != null)
        	this.colorPickerView.setTag(this.tag);
        setContentView(this.colorPickerView);
        setTitle("Pick a Color");
    }

	public View getView() {
		return colorPickerView;
	}

	public Object getTag() {
		return tag;
	}

	public void setTag(Object tag) {
		this.tag = tag;
		if (this.colorPickerView != null) {
			this.colorPickerView.setTag(this.tag);
		}
	}
}

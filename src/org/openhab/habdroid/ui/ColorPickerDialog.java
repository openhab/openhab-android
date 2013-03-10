/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.habdroid.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class ColorPickerDialog extends Dialog {
 
    public interface OnColorChangedListener {
        void colorChanged(int color, View v);
    }
 
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
            public void colorChanged(int color, View v) {
                mListener.colorChanged(color, v);
                dismiss();
            }
        };
        // TODO: add initial color
        this.colorPickerView = new ColorPicker(getContext());
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

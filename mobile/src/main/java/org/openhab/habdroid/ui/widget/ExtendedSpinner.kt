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

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

/*
 * An extended version of the Spinner class, which allows getting
 * callbacks for selection updates no matter whether the selection
 * has actually changed or not.
 */
class ExtendedSpinner : AppCompatSpinner {
    var onSelectionUpdatedListener: OnSelectionUpdatedListener? = null

    interface OnSelectionUpdatedListener {
        fun onSelectionUpdated(position: Int)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSelectionWithoutUpdateCallback(position: Int) {
        super.setSelection(position)
    }

    override fun setSelection(position: Int) {
        super.setSelection(position)
        onSelectionUpdatedListener?.onSelectionUpdated(position)
    }
}

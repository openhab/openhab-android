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

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    fun setSelectionWithoutUpdateCallback(position: Int) {
        super.setSelection(position)
    }

    override fun setSelection(position: Int) {
        super.setSelection(position)
        if (onSelectionUpdatedListener != null) {
            onSelectionUpdatedListener!!.onSelectionUpdated(position)
        }
    }
}

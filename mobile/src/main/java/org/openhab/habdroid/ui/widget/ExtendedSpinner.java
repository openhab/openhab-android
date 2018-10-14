package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSpinner;

/*
 * An extended version of the Spinner class, which allows getting
 * callbacks for selection updates no matter whether the selection
 * has actually changed or not.
 */
public class ExtendedSpinner extends AppCompatSpinner {
    public interface OnSelectionUpdatedListener {
        void onSelectionUpdated(int position);
    }

    private OnSelectionUpdatedListener mOnSelectionUpdatedListener;

    public ExtendedSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtendedSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnSelectionUpdatedListener(OnSelectionUpdatedListener listener) {
        mOnSelectionUpdatedListener = listener;
    }

    public void setSelectionWithoutUpdateCallback(int position) {
        super.setSelection(position);
    }

    @Override
    public void setSelection(int position) {
        super.setSelection(position);
        if (mOnSelectionUpdatedListener != null) {
            mOnSelectionUpdatedListener.onSelectionUpdated(position);
        }
    }
}

package org.openhab.habdroid.ui;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;

import java.util.ArrayList;

/**
 * Adapter class, which provides a way to add a click listener to a Spinner, which is always
 * triggered, when the user selects a value in the spinner, even if the value of the spinner does
 * not change.
 *
 * @param <T>
 */
class SpinnerClickAdapter<T> extends ArrayAdapter<T> implements SpinnerAdapter {
    private OnItemClickListener mListener;
    private Object mTag;

    SpinnerClickAdapter(Context context, @LayoutRes int layout, ArrayList<T> spinnerArray,
                               final Object tag, OnItemClickListener listener) {
        super(context, layout, spinnerArray);

        this.mTag = tag;
        this.mListener = listener;
    }

    @Override
    public View getDropDownView(final int position, @Nullable View convertView, @NonNull final ViewGroup
            parent) {
        View dropDownView = super.getDropDownView(position, convertView, parent);
        dropDownView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    parent.setTag(getTag());
                    mListener.onItemClick((AdapterView<?>) parent, v, position, getItemId(position));
                }
            }
        });

        return dropDownView;
    }

    public Object getTag() {
        return this.mTag;
    }
}
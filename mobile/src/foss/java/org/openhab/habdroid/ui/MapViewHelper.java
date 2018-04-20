package org.openhab.habdroid.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.openhab.habdroid.core.connection.Connection;

public class MapViewHelper {
    public static OpenHABWidgetAdapter.ViewHolder createViewHolder(LayoutInflater inflater,
            ViewGroup parent, Connection connection, OpenHABWidgetAdapter.ColorMapper colorMapper) {
        return new OpenHABWidgetAdapter.GenericViewHolder(inflater, parent, connection, colorMapper);
    }
}

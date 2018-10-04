package org.openhab.habdroid.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.openhab.habdroid.core.connection.Connection;

public class MapViewHelper {
    public static WidgetAdapter.ViewHolder createViewHolder(LayoutInflater inflater,
            ViewGroup parent, Connection connection, WidgetAdapter.ColorMapper colorMapper) {
        return new WidgetAdapter.GenericViewHolder(inflater, parent, connection, colorMapper);
    }
}

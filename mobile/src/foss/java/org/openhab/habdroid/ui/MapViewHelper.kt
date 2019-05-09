package org.openhab.habdroid.ui

import android.view.LayoutInflater
import android.view.ViewGroup

import org.openhab.habdroid.core.connection.Connection

object MapViewHelper {
    fun createViewHolder(inflater: LayoutInflater,
                         parent: ViewGroup, connection: Connection, colorMapper: WidgetAdapter.ColorMapper): WidgetAdapter.ViewHolder {
        return WidgetAdapter.GenericViewHolder(inflater, parent, connection, colorMapper)
    }
}

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
import android.view.ContextMenu
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ContextMenuAwareRecyclerView constructor(context: Context, attrs: AttributeSet) : RecyclerView(context, attrs) {
    private var contextMenuInfo: RecyclerContextMenuInfo? = null

    override fun getContextMenuInfo(): ContextMenu.ContextMenuInfo? {
        return contextMenuInfo
    }

    override fun showContextMenuForChild(view: View?): Boolean {
        val adapter = adapter
        val lp = view?.layoutParams
        if (adapter != null && lp is LayoutParams) {
            val position = lp.viewAdapterPosition
            if (position == NO_POSITION) {
                return false
            }
            val id = adapter.getItemId(position)
            contextMenuInfo = RecyclerContextMenuInfo(position, id)
        }
        return super.showContextMenuForChild(view)
    }

    data class RecyclerContextMenuInfo(val position: Int, val id: Long) : ContextMenu.ContextMenuInfo
}

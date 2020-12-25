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
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView

open class DividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val divider: Drawable?

    init {
        context.obtainStyledAttributes(null, intArrayOf(android.R.attr.listDivider)).apply {
            divider = getDrawable(0)
            recycle()
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        if (divider != null && !suppressDividerForChild(view, parent)) {
            outRect.bottom = divider.intrinsicHeight
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (divider == null) {
            return
        }

        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight

        parent.forEach { child ->
            if (!suppressDividerForChild(child, parent)) {
                val params = child.layoutParams as RecyclerView.LayoutParams

                val top = child.bottom + params.bottomMargin
                val bottom = top + divider.intrinsicHeight

                divider.setBounds(left, top, right, bottom)
                divider.draw(c)
            }
        }
    }

    protected open fun suppressDividerForChild(child: View, parent: RecyclerView): Boolean {
        val itemCount = parent.adapter?.itemCount ?: 0
        return itemCount > 0 && parent.getChildAdapterPosition(child) == itemCount - 1
    }
}

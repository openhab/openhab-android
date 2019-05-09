package org.openhab.habdroid.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class DividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val divider: Drawable?

    init {
        val a = context.obtainStyledAttributes(null, intArrayOf(android.R.attr.listDivider))
        divider = a.getDrawable(0)
        a.recycle()
    }

    override fun getItemOffsets(outRect: Rect, view: View,
                                parent: RecyclerView, state: RecyclerView.State) {
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

        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)

            if (suppressDividerForChild(child, parent)) {
                continue
            }

            val params = child.layoutParams as RecyclerView.LayoutParams

            val top = child.bottom + params.bottomMargin
            val bottom = top + divider.intrinsicHeight

            divider.setBounds(left, top, right, bottom)
            divider.draw(c)
        }
    }

    protected open fun suppressDividerForChild(child: View, parent: RecyclerView): Boolean {
        val itemCount = parent.adapter?.itemCount ?: 0
        return itemCount > 0 && parent.getChildAdapterPosition(child) == itemCount - 1
    }
}
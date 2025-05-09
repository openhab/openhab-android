/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.NestedScrollingChildHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

class RecyclerViewSwipeRefreshLayout(context: Context, attrs: AttributeSet) : SwipeRefreshLayout(context, attrs) {
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var downX: Float = 0F
    private var downY: Float = 0F
    private var childScrollableOnDown: Boolean = false
    private val parentOffsetInWindow = IntArray(2)
    private val nestedScrollingChildHelper = NestedScrollingChildHelper(this)
    private var horizontalSwipe: Boolean = false
    private var isOrWasUpSwipe: Boolean = false
    var recyclerView: RecyclerView? = null

    init {
        isNestedScrollingEnabled = true
    }

    override fun canChildScrollUp(): Boolean {
        val layoutManager = recyclerView?.layoutManager as LinearLayoutManager?
        val firstVisibleItem = layoutManager?.findFirstCompletelyVisibleItemPosition()
        return if (firstVisibleItem == null) super.canChildScrollUp() else firstVisibleItem != 0
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && shouldPreventRefresh()) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                horizontalSwipe = false
                isOrWasUpSwipe = false
                childScrollableOnDown = canChildScrollUp()
            }
            MotionEvent.ACTION_MOVE -> {
                val xDiff = abs(event.x - downX)
                val yDiff = event.y - downY

                if (yDiff < -touchSlop) {
                    isOrWasUpSwipe = true
                }
                if (horizontalSwipe || xDiff > touchSlop) {
                    horizontalSwipe = true
                    return false
                }
            }
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        // This method is called from the super constructor, where the helper isn't initialized yet
        @Suppress("UNNECESSARY_SAFE_CALL")
        nestedScrollingChildHelper?.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = nestedScrollingChildHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = nestedScrollingChildHelper.startNestedScroll(axes)

    override fun stopNestedScroll() {
        nestedScrollingChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean = nestedScrollingChildHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = nestedScrollingChildHelper.dispatchNestedScroll(
        dxConsumed,
        dyConsumed,
        dxUnconsumed,
        dyUnconsumed,
        offsetInWindow
    )

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean =
        nestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        nestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        nestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY)

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int) {
        if (shouldPreventRefresh()) {
            dispatchNestedScroll(
                dxConsumed,
                dyConsumed,
                dxUnconsumed,
                dyUnconsumed,
                parentOffsetInWindow
            )
        } else {
            super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
        }
    }

    private fun shouldPreventRefresh(): Boolean = childScrollableOnDown || isOrWasUpSwipe
}

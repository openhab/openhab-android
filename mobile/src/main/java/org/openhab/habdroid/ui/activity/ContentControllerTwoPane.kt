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

package org.openhab.habdroid.ui.activity

import android.os.Bundle
import android.view.View
import android.view.ViewStub
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import org.openhab.habdroid.R
import org.openhab.habdroid.model.LinkedPage
import org.openhab.habdroid.ui.MainActivity
import org.openhab.habdroid.ui.WidgetListFragment

// instantiated via reflection
@Suppress("UNUSED")
class ContentControllerTwoPane(activity: MainActivity) : ContentController(activity) {
    private lateinit var rightContentView: View

    override val fragmentForTitle
        get() = if (pageStack.size > 1) pageStack[pageStack.size - 2].second else sitemapFragment

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)
        rightContentView.isVisible = fm.findFragmentById(R.id.content_right) != null
    }

    override fun executeStateUpdate(reason: FragmentUpdateReason, allowStateLoss: Boolean) {
        var leftFragment = overridingFragment
        val rightFragment: WidgetListFragment?
        val rightPair: Pair<LinkedPage, WidgetListFragment>?

        when {
            leftFragment != null -> {
                rightFragment = null
                rightPair = null
            }
            sitemapFragment != null -> {
                rightPair = if (pageStack.empty()) null else pageStack.peek()
                leftFragment = fragmentForTitle
                rightFragment = rightPair?.second
            }
            else -> {
                leftFragment = defaultProgressFragment
                rightFragment = null
                rightPair = null
            }
        }

        val currentLeftFragment = fm.findFragmentById(R.id.content_left)
        val currentRightFragment = fm.findFragmentById(R.id.content_right)

        fm.commitNow(allowStateLoss) {
            if (currentLeftFragment != null && currentLeftFragment !== leftFragment) {
                remove(currentLeftFragment)
            }
            if (currentRightFragment != null && currentRightFragment !== rightFragment) {
                remove(currentRightFragment)
            }
        }

        fm.commit(allowStateLoss) {
            setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            if (leftFragment != null) {
                setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
                replace(R.id.content_left, leftFragment)
                if (leftFragment is WidgetListFragment) {
                    leftFragment.setHighlightedPageLink(rightPair?.first?.link)
                }
            }
            if (rightFragment != null) {
                setCustomAnimations(0, 0)
                replace(R.id.content_right, rightFragment)
                rightFragment.setHighlightedPageLink(null)
            }
        }

        rightContentView.isVisible = rightFragment != null
    }

    override fun openPage(page: LinkedPage, source: WidgetListFragment) {
        val currentLeftFragment = fm.findFragmentById(R.id.content_left)
        if (source === currentLeftFragment && !pageStack.empty()) {
            pageStack.pop()
        }
        super.openPage(page, source)
    }

    override fun inflateViews(stub: ViewStub) {
        stub.layoutResource = R.layout.content_twopane
        val view = stub.inflate()
        rightContentView = view.findViewById(R.id.content_right)
        rightContentView.isVisible = false
    }
}

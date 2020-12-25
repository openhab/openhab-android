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

package org.openhab.habdroid.ui

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.anything
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.R
import org.openhab.habdroid.TestWithoutIntro

@LargeTest
@RunWith(AndroidJUnit4::class)
class BasicWidgetTest : TestWithoutIntro() {
    @Test
    fun mainActivityTest() {
        val recyclerView = onView(withId(R.id.recyclerview))

        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(1))
                .check(matches(atPositionOnView(1, isDisplayed(), R.id.widgetlabel)))
                .check(matches(atPositionOnView(1, withText("First Floor"), R.id.widgetlabel)))

        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(7))
                .check(matches(atPositionOnView(7, isDisplayed(), R.id.widgetlabel)))
                .check(matches(
                        atPositionOnView(7, withText("Astronomical Data"), R.id.widgetlabel)))

        // does it show "garden"?
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(4))
                .check(matches(atPositionOnView(4, isDisplayed(), R.id.widgetlabel)))
                .check(matches(atPositionOnView(4, withText("Garden"), R.id.widgetlabel)))

        // open widget overview
        recyclerView
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(11, click()))

        // check whether selection widget appears and click on it
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(4))
                .check(matches(atPositionOnView(4, withText("Scene Selection"), R.id.widgetlabel)))
                .check(matches(atPositionOnView(4, isDisplayed(), R.id.spinner)))
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(4, onChildView(click(), R.id.spinner)))

        val appCompatCheckedTextView = onData(anything())
                .inAdapterView(withClassName(
                        `is`("androidx.appcompat.app.AlertController\$RecycleListView")))
                .atPosition(0)
        appCompatCheckedTextView.check(matches(withText("off")))
        appCompatCheckedTextView.perform(click())

        // check whether scene radio button group is present
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(5))
                .check(matches(atPositionOnView(5, isDisplayed(), R.id.switch_group)))

        // check whether switch is displayed
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(1))
                .check(matches(atPositionOnView(1, isDisplayed(), R.id.toggle)))

        // check whether slider is displayed
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(8))
                .check(matches(atPositionOnView(8, isDisplayed(), R.id.seekbar)))

        // check whether color control button is displayed
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(9))
                .check(matches(atPositionOnView(9, isDisplayed(), R.id.select_color_button)))

        // check whether roller shutter button is displayed
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(10))
                .check(matches(atPositionOnView(10, isDisplayed(), R.id.stop_button)))

        // check whether map view is displayed
        recyclerView
                .perform(scrollToPosition<RecyclerView.ViewHolder>(13))
                .check(matches(atPositionOnView(13, isDisplayed(), R.id.mapview)))
    }

    companion object {
        fun atPositionOnView(
            position: Int,
            itemMatcher: Matcher<View>,
            @IdRes targetViewId: Int
        ): Matcher<View> {
            return atPositionOnView(position, itemMatcher) { parent -> parent.findViewById(targetViewId) }
        }

        private fun atPositionOnView(
            position: Int,
            itemMatcher: Matcher<View>,
            childCb: (parent: View) -> View
        ): Matcher<View> {
            return object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
                override fun describeTo(description: Description) {
                    description.appendText("has view id $itemMatcher at position $position")
                }

                public override fun matchesSafely(recyclerView: RecyclerView): Boolean {
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                    val targetView = childCb(viewHolder!!.itemView)
                    return itemMatcher.matches(targetView)
                }
            }
        }

        fun onChildView(action: ViewAction, @IdRes targetViewId: Int): ViewAction {
            return object : ViewAction {
                override fun getConstraints(): Matcher<View>? {
                    return null
                }

                override fun getDescription(): String? {
                    return null
                }

                override fun perform(uiController: UiController, view: View) {
                    val v = view.findViewById<View>(targetViewId)
                    action.perform(uiController, v)
                }
            }
        }
    }
}

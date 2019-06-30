/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.InstrumentationRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4

import org.hamcrest.core.IsInstanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.R
import org.openhab.habdroid.TestWithoutIntro

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf

@LargeTest
@RunWith(AndroidJUnit4::class)
class NfcTest : TestWithoutIntro() {
    @Test
    fun nfcTest() {
        val recyclerView = onView(withId(R.id.recyclerview))
        val context = InstrumentationRegistry.getTargetContext()

        recyclerView.perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(10, longClick()))
        checkViewWithText(context, R.string.nfc_action_to_sitemap_page)
        pressBack()

        recyclerView.perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(10, click()))

        recyclerView.perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))

        val title = onView(allOf<View>(
                IsInstanceOf.instanceOf<View>(android.widget.TextView::class.java),
                withText(context.getString(R.string.nfc_dialog_title))))
        title.check(matches(withText(context.getString(R.string.nfc_dialog_title))))

        checkViewWithText(context, R.string.nfc_action_off)
        checkViewWithText(context, R.string.nfc_action_toggle)

        val onButton = checkViewWithText(context, R.string.nfc_action_on)
        onButton.perform(click())

        val imageView = onView(withId(R.id.nfc_watermark))
        imageView.check(matches(isDisplayed()))
    }

    private fun checkViewWithText(context: Context, @StringRes stringResId: Int): ViewInteraction {
        val title = context.getString(stringResId)
        val view = onView(withText(title))
        view.check(matches(withText(title)))
        return view
    }
}

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

package org.openhab.habdroid.ui

import androidx.core.content.edit
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.R
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isDemoModeEnabled

@LargeTest
@RunWith(AndroidJUnit4::class)
class IntroTest {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getPrefs()
        prefs.edit {
            putBoolean(PrefKeys.FIRST_START, true)
            putBoolean(PrefKeys.DEMO_MODE, false)
        }
    }

    @Test
    fun introTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        onView(allOf(withId(R.id.title), withText(R.string.intro_welcome), isDisplayed()))
            .check(matches(isDisplayed()))

        // Page through the info pages
        repeat(4) {
            onView(allOf(withId(R.id.next), withText(R.string.intro_next))).perform(click())
        }

        onView(allOf(withId(R.id.title), withText(R.string.intro_app_features), isDisplayed()))
            .check(matches(isDisplayed()))
        onView(allOf(withId(R.id.next), withText(R.string.intro_set_up))).perform(click())

        // Server selection
        onView(withText(R.string.intro_connect_title)).check(matches(isDisplayed()))
        assertFalse(context.getPrefs().isDemoModeEnabled())
        onView(withId(R.id.try_demo)).perform(scrollTo(), click())
    }
}

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

package org.openhab.habdroid

import android.view.View
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.intent.rule.IntentsTestRule

import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.openhab.habdroid.ui.MainActivity

abstract class ProgressbarAwareTest {
    @Rule
    @JvmField
    val activityTestRule = IntentsTestRule(MainActivity::class.java, true, false)

    private val progressbarIdlingResource: IdlingResource by lazy {
        val progressBar = activityTestRule.activity.findViewById<View>(R.id.toolbar_progress_bar)
        ProgressbarIdlingResource("Progressbar IdleResource", progressBar)
    }
    private val fragmentIdlingResource: IdlingResource by lazy {
        FragmentStatusIdlingResource("FragmentIdleResource", activityTestRule.activity.supportFragmentManager)
    }

    @Before
    open fun setup() {
        activityTestRule.launchActivity(null)
    }

    protected fun setupRegisterIdlingResources() {
        IdlingRegistry.getInstance().register(progressbarIdlingResource)
        IdlingRegistry.getInstance().register(fragmentIdlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(progressbarIdlingResource)
        IdlingRegistry.getInstance().unregister(fragmentIdlingResource)
    }
}

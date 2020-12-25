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

import android.content.Context
import android.speech.SpeechRecognizer
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.R
import org.openhab.habdroid.TestWithoutIntro

class VoiceRecognitionTest : TestWithoutIntro() {
    @Before
    fun checkVoiceRecognitionAvailableOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Voice recognition not available, skipping tests for it.",
                SpeechRecognizer.isRecognitionAvailable(context))
    }

    @Test
    fun checkVoiceAvailability() {
        val voice = onView(allOf<View>(
                withId(R.id.mainmenu_voice_recognition),
                withContentDescription("Voice recognition"),
                isDisplayed()))
        voice.check(matches(isDisplayed()))
    }

    override fun preselectSitemap(): Boolean {
        return true
    }
}

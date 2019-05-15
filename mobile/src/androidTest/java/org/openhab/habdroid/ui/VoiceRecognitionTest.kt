package org.openhab.habdroid.ui

import android.content.Context
import android.speech.SpeechRecognizer
import android.view.View
import androidx.test.InstrumentationRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.rule.ActivityTestRule

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.openhab.habdroid.R
import org.openhab.habdroid.TestWithoutIntro

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assume.assumeTrue

class VoiceRecognitionTest : TestWithoutIntro() {
    @Before
    fun checkVoiceRecognitionAvailableOnDevice() {
        val context = InstrumentationRegistry.getTargetContext()
        assumeTrue("Voice recognition not available, skipping tests for it.",
                SpeechRecognizer.isRecognitionAvailable(context))
    }

    @Test
    fun checkVoiceAvailbility() {
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

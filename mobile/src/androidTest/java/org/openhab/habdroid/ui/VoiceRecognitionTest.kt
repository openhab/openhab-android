package org.openhab.habdroid.ui

import android.speech.SpeechRecognizer
import android.view.View
import androidx.test.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.R
import org.openhab.habdroid.TestWithoutIntro

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

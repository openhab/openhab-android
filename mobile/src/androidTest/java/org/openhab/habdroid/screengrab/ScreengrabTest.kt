package org.openhab.habdroid.screengrab

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4

import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.R
import org.openhab.habdroid.TestWithoutIntro
import tools.fastlane.screengrab.locale.LocaleTestRule

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import tools.fastlane.screengrab.Screengrab.screenshot

@LargeTest
@RunWith(AndroidJUnit4::class)
class ScreengrabTest : TestWithoutIntro() {

    @Test
    fun test() {
        val recyclerView = onView(withId(R.id.recyclerview))

        screenshot("main-menu")

        // open first floor => Office
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, click()))
        screenshot("office")

        pressBack()
        pressBack()

        // open "Outside Temperature"
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(5, click()))
        screenshot("chart")
        pressBack()

        // open widget overview
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(10, click()))
        screenshot("widget-overview")
    }

    companion object {
        @ClassRule
        @JvmField
        val localeTestRule = LocaleTestRule()
    }
}

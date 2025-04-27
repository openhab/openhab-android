package org.openhab.habdroid.ui

import androidx.core.content.edit
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.R
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.getPrefs

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
        }
    }

    @Test
    fun introTest() {
        val textView = onView(
            allOf(
                withId(R.id.description),
                withText("A vendor and technology agnostic open source automation software for your home"),
                withParent(
                    allOf(
                        withId(com.github.appintro.R.id.main),
                        withParent(withId(com.github.appintro.R.id.view_pager))
                    )
                ),
                isDisplayed()
            )
        )
        textView.check(
            matches(withText("A vendor and technology agnostic open source automation software for your home"))
        )

        val materialTextView = onView(
            allOf(
                withId(R.id.description),
                withText("A vendor and technology agnostic open source automation software for your home"),
                isDisplayed()
            )
        )
        materialTextView.perform(click())

        val appCompatImageButton = onView(
            allOf(
                withId(com.github.appintro.R.id.next),
                withContentDescription("NEXT"),
                isDisplayed()
            )
        )
        appCompatImageButton.perform(click())

        val appCompatImageButton2 = onView(
            allOf(
                withId(com.github.appintro.R.id.next),
                withContentDescription("NEXT"),
                isDisplayed()
            )
        )
        appCompatImageButton2.perform(click())

        val appCompatImageButton3 = onView(
            allOf(
                withId(com.github.appintro.R.id.next),
                withContentDescription("NEXT"),
                isDisplayed()
            )
        )
        appCompatImageButton3.perform(click())

        val appCompatImageButton4 = onView(
            allOf(
                withId(com.github.appintro.R.id.next),
                withContentDescription("NEXT"),
                isDisplayed()
            )
        )
        appCompatImageButton4.perform(click())

        val textView2 = onView(
            allOf(
                withId(R.id.description),
                withText("For example turn on your lights when the alarm clock rings"),
                withParent(
                    allOf(
                        withId(com.github.appintro.R.id.main),
                        withParent(withId(com.github.appintro.R.id.view_pager))
                    )
                ),
                isDisplayed()
            )
        )
        textView2.check(matches(withText("For example turn on your lights when the alarm clock rings")))

        val materialButton = onView(
            allOf(
                withId(com.github.appintro.R.id.done),
                withText("DONE"),
                isDisplayed()
            )
        )
        materialButton.perform(click())
    }
}

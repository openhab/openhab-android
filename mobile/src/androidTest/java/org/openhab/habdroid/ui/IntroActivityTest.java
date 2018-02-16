package org.openhab.habdroid.ui;


import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.ViewInteraction;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import org.hamcrest.CoreMatchers;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithIntro;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.openhab.habdroid.TestUtils.childAtPosition;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class IntroActivityTest extends TestWithIntro {

    @Test
    public void appShowsIntro() {
        ViewInteraction textView = onView(
                allOf(withId(R.id.title), withText("Welcome to openHAB"),
                        childAtPosition(
                                allOf(withId(R.id.main),
                                        withParent(withId(R.id.view_pager))),
                                0),
                        isDisplayed()));
        textView.check(matches(withText("Welcome to openHAB")));

        ViewInteraction imageView = onView(
                allOf(withId(R.id.image),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.main),
                                        1),
                                0),
                        isDisplayed()));
        imageView.check(matches(isDisplayed()));

        ViewInteraction textView2 = onView(
                allOf(withId(R.id.description), withText("A vendor and technology agnostic open source automation software for your home"),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.main),
                                        2),
                                0),
                        isDisplayed()));
        textView2.check(matches(withText("A vendor and technology agnostic open source automation software for your home")));

        ViewInteraction button = onView(allOf(withId(R.id.skip)));
        button.check(matches(isDisplayed()));

        ViewInteraction imageButton = onView(allOf(withId(R.id.next)));
        imageButton.check(matches(isDisplayed()));

        // skip intro
        ViewInteraction appCompatButton = onView(
                allOf(withId(R.id.skip), withText("SKIP"),
                        childAtPosition(
                                allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                1),
                        isDisplayed()));
        appCompatButton.perform(click());

        appCompatButton.check(doesNotExist());

        mActivityTestRule.finishActivity();
        mActivityTestRule.launchActivity(null);

        IdlingRegistry.getInstance().register(getProgressbarIdlingResource());

        // Do we see the sitemap?
        ViewInteraction firstfloor = onView(
                CoreMatchers.allOf(withId(R.id.widgetlabel), withText("First Floor"),
                        childAtPosition(
                                CoreMatchers.allOf(withId(R.id.groupleftlayout),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                                                0)),
                                1),
                        isDisplayed()));
        firstfloor.check(matches(withText("First Floor")));
    }

    @Test
    public void goThroughIntro() {
        // click next
        ViewInteraction appCompatImageButton = onView(
                CoreMatchers.allOf(withId(R.id.next),
                        childAtPosition(
                                CoreMatchers.allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                3),
                        isDisplayed()));
        appCompatImageButton.perform(click());

        // click next
        ViewInteraction appCompatImageButton2 = onView(
                CoreMatchers.allOf(withId(R.id.next),
                        childAtPosition(
                                CoreMatchers.allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                3),
                        isDisplayed()));
        appCompatImageButton2.perform(click());

        // close intro
        ViewInteraction appCompatButton = onView(
                CoreMatchers.allOf(withId(R.id.done), withText("DONE"),
                        childAtPosition(
                                CoreMatchers.allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                4),
                        isDisplayed()));
        appCompatButton.perform(click());

        appCompatButton.check(doesNotExist());

        mActivityTestRule.finishActivity();
        mActivityTestRule.launchActivity(null);

        IdlingRegistry.getInstance().register(getProgressbarIdlingResource());

        // Do we see the sitemap?
        ViewInteraction firstfloor = onView(
                CoreMatchers.allOf(withId(R.id.widgetlabel), withText("First Floor"),
                        childAtPosition(
                                CoreMatchers.allOf(withId(R.id.groupleftlayout),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                                                0)),
                                1),
                        isDisplayed()));
        firstfloor.check(matches(withText("First Floor")));
    }
}

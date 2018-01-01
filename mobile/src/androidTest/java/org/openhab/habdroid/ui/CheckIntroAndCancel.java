package org.openhab.habdroid.ui;


import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class CheckIntroAndCancel {

    @Rule
    public ActivityTestRule<OpenHABMainActivity> mActivityTestRule = new ActivityTestRule<>
            (OpenHABMainActivity.class, true, false);

    @Before
    public void setup() {
        PreferenceManager
                .getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit()
                .putBoolean(Constants.PREFERENCE_FIRST_START, true)
                .commit();

        mActivityTestRule.launchActivity(null);
    }

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

        // do we have sitemap selection popup?
        ViewInteraction linearLayout = onView(
                Matchers.allOf(IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class),
                        childAtPosition(
                                Matchers.allOf(IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class),
                                                0)),
                                0),
                        isDisplayed()));
        linearLayout.check(matches(isDisplayed()));
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}

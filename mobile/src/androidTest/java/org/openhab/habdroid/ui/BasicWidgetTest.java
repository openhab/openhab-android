package org.openhab.habdroid.ui;


import android.support.test.espresso.DataInteraction;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicWidgetTest {

    @Rule
    public ActivityTestRule<OpenHABMainActivity> mActivityTestRule = new ActivityTestRule<>(OpenHABMainActivity.class);

    @Test
    public void openHABMainActivityTest2() {
        // click next
        ViewInteraction appCompatImageButton = onView(
                allOf(withId(R.id.next),
                        childAtPosition(
                                allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                3),
                        isDisplayed()));
        appCompatImageButton.perform(click());

        // click next
        ViewInteraction appCompatImageButton2 = onView(
                allOf(withId(R.id.next),
                        childAtPosition(
                                allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                3),
                        isDisplayed()));
        appCompatImageButton2.perform(click());

        // close intro
        ViewInteraction appCompatButton = onView(
                allOf(withId(R.id.done), withText("DONE"),
                        childAtPosition(
                                allOf(withId(R.id.bottomContainer),
                                        childAtPosition(
                                                withId(R.id.bottom),
                                                1)),
                                4),
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

        // does it contain "demo"?
        ViewInteraction textView = onView(
                Matchers.allOf(withId(android.R.id.text1), withText("demo"),
                        childAtPosition(
                                Matchers.allOf(IsInstanceOf.<View>instanceOf(android.widget.ListView.class),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.FrameLayout.class),
                                                0)),
                                0),
                        isDisplayed()));
        textView.check(matches(withText("demo")));

        // click on demo
        DataInteraction appCompatTextView = onData(anything())
                .inAdapterView(allOf(withClassName(is("com.android.internal.app.AlertController$RecycleListView")),
                        childAtPosition(
                                withClassName(is("android.widget.FrameLayout")),
                                0)))
                .atPosition(0);
        appCompatTextView.perform(click());

        ViewInteraction mainmenu = onView(
                allOf(withText("Main Menu"),
                        childAtPosition(
                                allOf(withId(R.id.openhab_toolbar),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class),
                                                0)),
                                1),
                        isDisplayed()));
        mainmenu.check(matches(withText("Main Menu")));

        ViewInteraction voice = onView(
                allOf(withId(R.id.mainmenu_voice_recognition), withContentDescription("Voice recognition"), isDisplayed()));
        voice.check(matches(isDisplayed()));

        ViewInteraction option = onView(
                allOf(withContentDescription("More options"), isDisplayed()));
        option.check(matches(isDisplayed()));

        ViewInteraction firstfloor = onView(
                allOf(withId(R.id.widgetlabel), withText("First Floor"),
                        childAtPosition(
                                allOf(withId(R.id.groupleftlayout),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                                                0)),
                                1),
                        isDisplayed()));
        firstfloor.check(matches(withText("First Floor")));

        ViewInteraction astro = onView(
                allOf(withId(R.id.widgetlabel), withText("Astronomical Data"),
                        childAtPosition(
                                allOf(withId(R.id.textleftlayout),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                                                0)),
                                1),
                        isDisplayed()));
        astro.check(matches(withText("Astronomical Data")));

        // does it show "garden"?
        ViewInteraction garden = onView(
                allOf(withId(R.id.widgetlabel), withText("Garden"),
                        childAtPosition(
                                allOf(withId(R.id.groupleftlayout),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                                                0)),
                                1),
                        isDisplayed()));
        garden.check(matches(withText("Garden")));


        DataInteraction relativeLayout = onData(anything())
                .inAdapterView(Matchers.allOf(withId(android.R.id.list),
                        childAtPosition(
                                childAtPosition(
                                        Matchers.allOf(withId(R.id.pager),
                                                childAtPosition(
                                                        childAtPosition(
                                                                Matchers.allOf(withId(R.id.drawer_layout),
                                                                        childAtPosition(
                                                                                childAtPosition(
                                                                                        withId(android.R.id.content),
                                                                                        0),
                                                                                1)),
                                                                0),
                                                        0)),
                                        0),
                                0)))
                .atPosition(10);
        relativeLayout.perform(click());

        ViewInteraction appCompatSpinner = onView(
                Matchers.allOf(withId(R.id.selectionspinner),
                        childAtPosition(
                                childAtPosition(
                                        withParent(Matchers.allOf(withId(android.R.id.list),
                                                childAtPosition(
                                                        childAtPosition(
                                                                Matchers.allOf(withId(R.id.pager),
                                                                        childAtPosition(
                                                                                childAtPosition(
                                                                                        withId(R.id.drawer_layout),
                                                                                        0),
                                                                                0)),
                                                                1),
                                                        0))),
                                        0),
                                1),
                        isDisplayed()));
        appCompatSpinner.perform(click());

        ViewInteraction textView3 = onView(
                Matchers.allOf(IsInstanceOf.<View>instanceOf(android.widget.TextView.class), withText("Scene Selection"), isDisplayed()));
        textView3.check(matches(withText("Scene Selection")));

        DataInteraction appCompatCheckedTextView = onData(anything())
                .inAdapterView(Matchers.allOf(withClassName(is("com.android.internal.app.AlertController$RecycleListView")),
                        childAtPosition(
                                Matchers.allOf(withClassName(is("android.widget.FrameLayout")),
                                        childAtPosition(
                                                Matchers.allOf(withClassName(is("com.android.internal.widget.AlertDialogLayout")),
                                                        childAtPosition(
                                                                Matchers.allOf(withId(android.R.id.content),
                                                                        childAtPosition(
                                                                                withClassName(is("android.widget.FrameLayout")),
                                                                                0)),
                                                                0)),
                                                1)),
                                0)))
                .atPosition(0);
        appCompatCheckedTextView.perform(click());

        /*ViewInteraction radioButton = onView(
                Matchers.allOf(IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                        withId(R.id.sectionswitchradiogroup), isDisplayed()));
        radioButton.check(matches(isDisplayed()));*/

        ViewInteraction switch_ = onView(
                Matchers.allOf(withId(R.id.switchswitch),
                        childAtPosition(
                                childAtPosition(
                                        childAtPosition(
                                                Matchers.allOf(withId(android.R.id.list),
                                                        childAtPosition(
                                                                withParent(Matchers.allOf(withId(R.id.pager),
                                                                        childAtPosition(
                                                                                childAtPosition(
                                                                                        withId(R.id.drawer_layout),
                                                                                        0),
                                                                                0))),
                                                                0)),
                                                1),
                                        0),
                                1),
                        isDisplayed()));
        switch_.check(matches(isDisplayed()));

        ViewInteraction button = onView(
                Matchers.allOf(withId(R.id.setpointbutton_minus),
                        childAtPosition(
                                childAtPosition(
                                        childAtPosition(
                                                childAtPosition(
                                                        Matchers.allOf(withId(android.R.id.list),
                                                                childAtPosition(
                                                                        withParent(Matchers.allOf(withId(R.id.pager),
                                                                                childAtPosition(
                                                                                        IsInstanceOf.<View>instanceOf(android.widget.RelativeLayout.class),
                                                                                        0))),
                                                                        0)),
                                                        6),
                                                0),
                                        1),
                                0),
                        isDisplayed()));
        button.check(matches(isDisplayed()));

        ViewInteraction seekBar = onView(
                Matchers.allOf(withId(R.id.sliderseekbar),
                        childAtPosition(
                                childAtPosition(
                                        childAtPosition(
                                                Matchers.allOf(withId(android.R.id.list),
                                                        childAtPosition(
                                                                withParent(Matchers.allOf(withId(R.id.pager),
                                                                        childAtPosition(
                                                                                childAtPosition(
                                                                                        withId(R.id.drawer_layout),
                                                                                        0),
                                                                                0))),
                                                                0)),
                                                8),
                                        0),
                                1),
                        isDisplayed()));
        seekBar.check(matches(isDisplayed()));


        ViewInteraction imageButton = onView(
                Matchers.allOf(withId(R.id.colorbutton_color), isDisplayed()));
        imageButton.check(matches(isDisplayed()));

        ViewInteraction imageButton2 = onView(
                Matchers.allOf(withId(R.id.rollershutterbutton_stop), isDisplayed()));
        imageButton2.check(matches(isDisplayed()));
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

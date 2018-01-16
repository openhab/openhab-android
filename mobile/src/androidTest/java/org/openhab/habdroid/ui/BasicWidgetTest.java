package org.openhab.habdroid.ui;


import android.support.constraint.ConstraintLayout;
import android.support.test.espresso.DataInteraction;
import android.support.test.espresso.ViewInteraction;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import org.hamcrest.Matchers;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.hasSibling;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withChild;
import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withResourceName;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;
import static org.openhab.habdroid.TestUtils.childAtPosition;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicWidgetTest extends TestWithoutIntro {

    @Test
    public void openHABMainActivityTest() throws InterruptedException {
        ViewInteraction firstfloor = onView(
                allOf(withId(R.id.widgetlabel), withText("First Floor"),
                        childAtPosition(
                                withId(R.id.groupleftlayout), 1),
                        isDisplayed()));
        firstfloor.check(matches(withText("First Floor")));

        ViewInteraction astro = onView(
                allOf(withId(R.id.widgetlabel), withText("Astronomical Data"),
                        childAtPosition(
                                allOf(withId(R.id.textleftlayout),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(ConstraintLayout.class),
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
                .inAdapterView(withId(android.R.id.list))
                .atPosition(10);
        relativeLayout.perform(click());

        ViewInteraction appCompatSpinner = onView(Matchers.allOf(withId(R.id.selectionspinner),
                        isDisplayed()));
        appCompatSpinner.perform(click());

        ViewInteraction textView3 = onView(
                Matchers.allOf(IsInstanceOf.<View>instanceOf(android.widget.TextView.class), withText("Scene Selection"), isDisplayed()));
        textView3.check(matches(withText("Scene Selection")));

        DataInteraction appCompatCheckedTextView = onData(anything())
                .inAdapterView(withClassName(
                        is("com.android.internal.app.AlertController$RecycleListView")))
                .atPosition(0);
        appCompatCheckedTextView.check(matches(withText("off")));
        appCompatCheckedTextView.perform(click());

        ViewInteraction switch_ = onView(
                Matchers.allOf(withId(R.id.switchswitch), isDisplayed()));
        switch_.check(matches(isDisplayed()));

        ViewInteraction seekBar = onView(
                Matchers.allOf(withId(R.id.sliderseekbar),
                        hasSibling(
                                allOf(
                                        withResourceName("sliderleftlayout"),
                                        withChild(
                                                allOf(withId(R.id.widgetlabel), withText("Dimmer "))
                                        )
                                )
                        ),
                        isDisplayed()));
        seekBar.check(matches(isDisplayed()));


        ViewInteraction imageButton = onView(
                Matchers.allOf(withId(R.id.colorbutton_color), isDisplayed()));
        imageButton.check(matches(isDisplayed()));

        ViewInteraction imageButton2 = onView(
                Matchers.allOf(withId(R.id.rollershutterbutton_stop), isDisplayed()));
        imageButton2.check(matches(isDisplayed()));
    }
}

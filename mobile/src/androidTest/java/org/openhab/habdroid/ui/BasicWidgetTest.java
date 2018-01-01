package org.openhab.habdroid.ui;


import android.preference.PreferenceManager;
import android.support.constraint.ConstraintLayout;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.DataInteraction;
import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import org.hamcrest.Matchers;
import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.OpenHABProgressbarIdlingResource;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.registerIdlingResources;
import static android.support.test.espresso.Espresso.unregisterIdlingResources;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;
import static org.openhab.habdroid.TestUtils.childAtPosition;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicWidgetTest {

    @Rule
    public ActivityTestRule<OpenHABMainActivity> mActivityTestRule = new ActivityTestRule<>
            (OpenHABMainActivity.class, true, false);

    private IdlingResource mProgressbarIdlingResource;

    @Before
    public void setup() {
        PreferenceManager
                .getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit()
                .putString(Constants.PREFERENCE_SITEMAP, "")
                .putBoolean(Constants.PREFERENCE_FIRST_START, false)
                .commit();

        mActivityTestRule.launchActivity(null);
    }

    @Test
    public void openHABMainActivityTest() throws InterruptedException {
        View progressBar = mActivityTestRule.getActivity().findViewById(R.id.toolbar_progress_bar);
        mProgressbarIdlingResource = new OpenHABProgressbarIdlingResource("Progressbar " +
                "IdleResource", progressBar);
        registerIdlingResources(mProgressbarIdlingResource);

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
                                IsInstanceOf.<View>instanceOf(android.widget.ListView.class),
                                0),
                        isDisplayed()));
        textView.check(matches(withText("demo")));

        // click on demo
        DataInteraction appCompatTextView = onData(anything())
                .inAdapterView(withClassName(
                        is("com.android.internal.app.AlertController$RecycleListView")))
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
                .inAdapterView(withClassName(
                        is("com.android.internal.app.AlertController$RecycleListView")))
                .atPosition(0);
        appCompatCheckedTextView.check(matches(withText("off")));
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

    @After
    public void unregisterIdlingResource() {
        if (mProgressbarIdlingResource != null)
            unregisterIdlingResources(mProgressbarIdlingResource);
    }
}

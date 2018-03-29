package org.openhab.habdroid.screengrab;

import android.support.annotation.IdRes;
import android.support.test.espresso.DataInteraction;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;
import org.openhab.habdroid.ui.BasicWidgetTest;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;
import static org.openhab.habdroid.ui.BasicWidgetTest.atPositionOnView;
import static org.openhab.habdroid.ui.BasicWidgetTest.onChildView;
import static tools.fastlane.screengrab.Screengrab.*;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ScreengrabTest extends TestWithoutIntro {
    @ClassRule
    public static final LocaleTestRule localeTestRule = new LocaleTestRule();

    @Test
    public void test() {
        //Screengrab.setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());

        ViewInteraction recyclerView = onView(withId(R.id.recyclerview));

        screenshot("main-menu");

        // open first floor => Office
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition(1, click()));
        screenshot("office");

        pressBack();
        pressBack();

        // open "Outside Temperature"
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition(5, click()));
        screenshot("chart");
        pressBack();

        // open widget overview
        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition(10, click()));
        screenshot("widget-overview");

        /*// open nfc selection
        recyclerView.perform(actionOnItemAtPosition(1, longClick()));
        screenshot("nfc-selection");
        pressBack();

        // click on selection widget
        recyclerView
                .perform(RecyclerViewActions.scrollToPosition(4))
                .perform(RecyclerViewActions.actionOnItemAtPosition(4, onChildView(click(), R.id.selectionspinner)));
        screenshot("selection");
        pressBack();

        // open color picker
        recyclerView
                .perform(RecyclerViewActions.scrollToPosition(9))
                .perform(RecyclerViewActions.actionOnItemAtPosition(9, onChildView(click(), R.id.colorbutton_color)));
        screenshot("color");
        pressBack();

        if (BuildConfig.FLAVOR.equals("full")) {
            // check whether map view is displayed
            recyclerView
                    .perform(RecyclerViewActions.scrollToPosition(13))
                    .check(matches(atPositionOnView(13, isDisplayed(), "MapView")));
            screenshot("mapview");
        }*/
    }
}

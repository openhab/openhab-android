package org.openhab.habdroid.screengrab;

import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import tools.fastlane.screengrab.locale.LocaleTestRule;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static tools.fastlane.screengrab.Screengrab.*;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ScreengrabTest extends TestWithoutIntro {
    @ClassRule
    public static final LocaleTestRule localeTestRule = new LocaleTestRule();

    @Test
    public void test() {
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
    }
}

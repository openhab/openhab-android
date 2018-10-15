package org.openhab.habdroid.screengrab;

import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static tools.fastlane.screengrab.Screengrab.screenshot;

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

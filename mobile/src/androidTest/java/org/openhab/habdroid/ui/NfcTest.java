package org.openhab.habdroid.ui;

import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.core.IsInstanceOf;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class NfcTest extends TestWithoutIntro {
    @ClassRule
    public static final LocaleTestRule localeTestRule = new LocaleTestRule();

    @Test
    public void nfcTest() throws InterruptedException {
        ViewInteraction recyclerView = onView(withId(R.id.recyclerview));

        recyclerView
                .perform(RecyclerViewActions.actionOnItemAtPosition(10, click()));

        recyclerView.perform(actionOnItemAtPosition(10, click()));

        recyclerView.perform(actionOnItemAtPosition(1, longClick()));

        ViewInteraction title = onView(allOf(
                IsInstanceOf.instanceOf(android.widget.TextView.class),
                withText("Write NFC tag action for this element")));
        title.check(matches(withText("Write NFC tag action for this element")));

        ViewInteraction onButton = onView(withText("On"));
        onButton.check(matches(withText("On")));

        ViewInteraction offButton = onView(withText("Off"));
        offButton.check(matches(withText("Off")));

        ViewInteraction toggleButton = onView(withText("Toggle"));
        toggleButton.check(matches(withText("Toggle")));

        ViewInteraction sitemapButton = onView(withText("Navigate to Sitemap page"));
        sitemapButton.check(matches(withText("Navigate to Sitemap page")));

        Screengrab.screenshot("nfc_select");

        onButton.perform(click());

        Screengrab.screenshot("nfc_activity");

        ViewInteraction imageView = onView(withId(R.id.nfc_watermark));
        imageView.check(matches(isDisplayed()));
    }
}

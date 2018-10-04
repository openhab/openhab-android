package org.openhab.habdroid.ui;

import android.content.Context;
import android.support.annotation.StringRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

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
    @Test
    public void nfcTest() {
        ViewInteraction recyclerView = onView(withId(R.id.recyclerview));
        Context context = InstrumentationRegistry.getTargetContext();

        recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition(10, click()));

        recyclerView.perform(actionOnItemAtPosition(10, click()));

        recyclerView.perform(actionOnItemAtPosition(1, longClick()));

        ViewInteraction title = onView(allOf(
                IsInstanceOf.instanceOf(android.widget.TextView.class),
                withText(context.getString(R.string.nfc_dialog_title))));
        title.check(matches(withText(context.getString(R.string.nfc_dialog_title))));

        checkViewWithText(context, R.string.nfc_action_off);
        checkViewWithText(context, R.string.nfc_action_toggle);
        checkViewWithText(context, R.string.nfc_action_to_sitemap_page);

        ViewInteraction onButton = checkViewWithText(context, R.string.nfc_action_on);
        onButton.perform(click());

        ViewInteraction imageView = onView(withId(R.id.nfc_watermark));
        imageView.check(matches(isDisplayed()));
    }

    private ViewInteraction checkViewWithText(Context context, @StringRes int stringResId) {
        String title = context.getString(stringResId);
        ViewInteraction view = onView(withText(title));
        view.check(matches(withText(title)));
        return view;
    }
}

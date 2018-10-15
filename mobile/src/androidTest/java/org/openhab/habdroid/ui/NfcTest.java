package org.openhab.habdroid.ui;

import android.content.Context;
import androidx.annotation.StringRes;
import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
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

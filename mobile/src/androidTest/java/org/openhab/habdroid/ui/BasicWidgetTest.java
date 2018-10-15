package org.openhab.habdroid.ui;

import android.view.View;
import androidx.annotation.IdRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.TestWithoutIntro;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicWidgetTest extends TestWithoutIntro {
    @Test
    public void mainActivityTest() {
        ViewInteraction recyclerView = onView(withId(R.id.recyclerview));

        recyclerView
                .perform(scrollToPosition(0))
                .check(matches(atPositionOnView(0, isDisplayed(), R.id.widgetlabel)))
                .check(matches(atPositionOnView(0, withText("First Floor"), R.id.widgetlabel)));

        recyclerView
                .perform(scrollToPosition(6))
                .check(matches(atPositionOnView(6, isDisplayed(), R.id.widgetlabel)))
                .check(matches(
                        atPositionOnView(6, withText("Astronomical Data"), R.id.widgetlabel)));

        // does it show "garden"?
        recyclerView
                .perform(scrollToPosition(3))
                .check(matches(atPositionOnView(3, isDisplayed(), R.id.widgetlabel)))
                .check(matches(atPositionOnView(3, withText("Garden"), R.id.widgetlabel)));

        // open widget overview
        recyclerView
                .perform(actionOnItemAtPosition(10, click()));

        // check whether selection widget appears and click on it
        recyclerView
                .perform(scrollToPosition(4))
                .check(matches(atPositionOnView(4, withText("Scene Selection"), R.id.widgetlabel)))
                .check(matches(atPositionOnView(4, isDisplayed(), R.id.spinner)))
                .perform(actionOnItemAtPosition(4, onChildView(click(), R.id.spinner)));

        DataInteraction appCompatCheckedTextView = onData(anything())
                .inAdapterView(withClassName(
                        is("com.android.internal.app.AlertController$RecycleListView")))
                .atPosition(0);
        appCompatCheckedTextView.check(matches(withText("off")));
        appCompatCheckedTextView.perform(click());

        // check whether scene radio button group is present
        recyclerView
                .perform(scrollToPosition(5))
                .check(matches(atPositionOnView(5, isDisplayed(), R.id.switchgroup)));

        // check whether switch is displayed
        recyclerView
                .perform(scrollToPosition(1))
                .check(matches(atPositionOnView(1, isDisplayed(), R.id.toggle)));

        // check whether slider is displayed
        recyclerView
                .perform(scrollToPosition(8))
                .check(matches(atPositionOnView(8, isDisplayed(), R.id.seekbar)));

        // check whether color control button is displayed
        recyclerView
                .perform(scrollToPosition(9))
                .check(matches(atPositionOnView(9, isDisplayed(), R.id.select_color_button)));

        // check whether roller shutter button is displayed
        recyclerView
                .perform(scrollToPosition(10))
                .check(matches(atPositionOnView(10, isDisplayed(), R.id.stop_button)));

        if (BuildConfig.FLAVOR.equals("full")) {
            // check whether map view is displayed
            recyclerView
                    .perform(scrollToPosition(13))
                    .check(matches(atPositionOnView(13, isDisplayed(), "MapView")));
        }
    }

    public interface ChildViewCallback {
        View findChild(View parent);
    }

    public static Matcher<View> atPositionOnView(final int position,
            final Matcher<View> itemMatcher, @IdRes final int targetViewId) {
        return atPositionOnView(position, itemMatcher, parent -> parent.findViewById(targetViewId));
    }

    public static Matcher<View> atPositionOnView(final int position,
            final Matcher<View> itemMatcher, final String tag) {
        return atPositionOnView(position, itemMatcher, parent -> parent.findViewWithTag(tag));
    }

    public static Matcher<View> atPositionOnView(final int position,
            final Matcher<View> itemMatcher, final ChildViewCallback childCb) {
        return new BoundedMatcher<View, RecyclerView>(RecyclerView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("has view id " + itemMatcher + " at position " + position);
            }

            @Override
            public boolean matchesSafely(final RecyclerView recyclerView) {
                RecyclerView.ViewHolder viewHolder =
                        recyclerView.findViewHolderForAdapterPosition(position);
                View targetView = childCb.findChild(viewHolder.itemView);
                return itemMatcher.matches(targetView);
            }
        };
    }

    public static ViewAction onChildView(final ViewAction action, @IdRes final int targetViewId) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public void perform(UiController uiController, View view) {
                View v = view.findViewById(targetViewId);
                action.perform(uiController, v);
            }
        };
    }
}

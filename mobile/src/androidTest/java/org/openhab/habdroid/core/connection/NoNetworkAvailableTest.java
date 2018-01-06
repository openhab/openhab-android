package org.openhab.habdroid.core.connection;

import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.view.View;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.exception.NetworkNotAvailableException;
import org.openhab.habdroid.ui.OpenHABMainActivity;

import java.util.HashMap;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.openhab.habdroid.TestUtils.childAtPosition;

public class NoNetworkAvailableTest {
    @Rule
    public ActivityTestRule<OpenHABMainActivity> mActivityTestRule = new ActivityTestRule<>
            (OpenHABMainActivity.class,  true, false);

    private String noNetworkMessage;

    @Before
    public void ensureNoNetworkAvailable() {
        noNetworkMessage = InstrumentationRegistry.getTargetContext().getString(R.string
                .error_network_not_available);

        ConnectionFactory mockConnectionFactory = Mockito.mock(ConnectionFactory.class);
        Mockito.when(mockConnectionFactory.getAvailableConnection()).thenThrow(new
                NetworkNotAvailableException(noNetworkMessage));
        mockConnectionFactory.cachedConnections = new HashMap<>();
        ConnectionFactory.InstanceHolder.INSTANCE = mockConnectionFactory;

        mActivityTestRule.launchActivity(null);
    }

    @Test
    public void testNoNetworkFragmentShown() {
        ViewInteraction noNetwork = onView(allOf(withId(R.id.network_error_description), isDisplayed()));

        noNetwork.check(matches(withText(noNetworkMessage)));
    }

    @Test
    public void testNoDrawerWhenNoNetwork() {
        ViewInteraction noDrawer = onView(allOf(withId(R.id.drawer_layout), not(isDisplayed())));
        noDrawer.check(doesNotExist());

        ViewInteraction mainmenu = onView(
                allOf(withText("Main Menu"),
                        childAtPosition(
                                allOf(withId(R.id.openhab_toolbar),
                                        childAtPosition(
                                                IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class),
                                                0)),
                                1),
                        not(isDisplayed())));
        mainmenu.check(doesNotExist());
    }
}

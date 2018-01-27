package org.openhab.habdroid;

import android.support.test.espresso.IdlingResource;
import android.support.test.rule.ActivityTestRule;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.openhab.habdroid.ui.OpenHABMainActivity;

import static android.support.test.espresso.Espresso.registerIdlingResources;
import static android.support.test.espresso.Espresso.unregisterIdlingResources;

public abstract class ProgressbarAwareTest {

    @Rule
    public ActivityTestRule<OpenHABMainActivity> mActivityTestRule = new ActivityTestRule<>
            (OpenHABMainActivity.class, true, false);

    private IdlingResource mProgressbarIdlingResource;

    @Before
    public void setup() {
        mActivityTestRule.launchActivity(null);

        View progressBar = mActivityTestRule.getActivity().findViewById(R.id.toolbar_progress_bar);
        mProgressbarIdlingResource = new OpenHABProgressbarIdlingResource("Progressbar " +
                "IdleResource", progressBar);
        registerIdlingResources(mProgressbarIdlingResource);
    }

    @After
    public void unregisterIdlingResource() {
        if (mProgressbarIdlingResource != null)
            unregisterIdlingResources(mProgressbarIdlingResource);
    }
}

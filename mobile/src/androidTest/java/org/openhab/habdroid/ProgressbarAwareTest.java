package org.openhab.habdroid;

import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.openhab.habdroid.ui.OpenHABMainActivity;

import static android.support.test.espresso.Espresso.registerIdlingResources;
import static android.support.test.espresso.Espresso.unregisterIdlingResources;

public abstract class ProgressbarAwareTest {

    @Rule
    public IntentsTestRule<OpenHABMainActivity> mActivityTestRule = new IntentsTestRule<>
            (OpenHABMainActivity.class,  true, false);

    private IdlingResource mProgressbarIdlingResource;

    @Before
    public void setup() {
        mActivityTestRule.launchActivity(null);

        setupRegisterIdlingResources();
    }

    protected void setupRegisterIdlingResources() {
        registerIdlingResources(getProgressbarIdlingResource());
    }

    protected IdlingResource getProgressbarIdlingResource() {
        if (mProgressbarIdlingResource == null) {
            View progressBar = mActivityTestRule.getActivity().findViewById(R.id.toolbar_progress_bar);
            mProgressbarIdlingResource = new OpenHABProgressbarIdlingResource("Progressbar " +
                    "IdleResource", progressBar);
        }
        return mProgressbarIdlingResource;
    }

    @After
    public void unregisterIdlingResource() {
        if (mProgressbarIdlingResource != null)
            unregisterIdlingResources(mProgressbarIdlingResource);
    }
}

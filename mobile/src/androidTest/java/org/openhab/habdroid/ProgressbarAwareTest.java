package org.openhab.habdroid;

import android.view.View;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.intent.rule.IntentsTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.openhab.habdroid.ui.MainActivity;

public abstract class ProgressbarAwareTest {
    @Rule
    public IntentsTestRule<MainActivity> mActivityTestRule =
            new IntentsTestRule<>(MainActivity.class,  true, false);

    private IdlingResource mProgressbarIdlingResource;
    private IdlingResource mFragmentIdlingResource;

    @Before
    public void setup() {
        mActivityTestRule.launchActivity(null);
    }

    protected void setupRegisterIdlingResources() {
        IdlingRegistry.getInstance().register(getProgressbarIdlingResource());
        IdlingRegistry.getInstance().register(getFragmentIdlingResource());
    }

    protected IdlingResource getProgressbarIdlingResource() {
        if (mProgressbarIdlingResource == null) {
            final View progressBar =
                    mActivityTestRule.getActivity().findViewById(R.id.toolbar_progress_bar);
            mProgressbarIdlingResource =
                    new ProgressbarIdlingResource("Progressbar IdleResource", progressBar);
        }
        return mProgressbarIdlingResource;
    }

    protected IdlingResource getFragmentIdlingResource() {
        if (mFragmentIdlingResource == null) {
            mFragmentIdlingResource = new FragmentStatusIdlingResource("FragmentIdleResource",
                    mActivityTestRule.getActivity().getSupportFragmentManager());
        }
        return mFragmentIdlingResource;
    }

    @After
    public void unregisterIdlingResource() {
        if (mProgressbarIdlingResource != null) {
            IdlingRegistry.getInstance().unregister(mProgressbarIdlingResource);
        }
        if (mFragmentIdlingResource != null) {
            IdlingRegistry.getInstance().unregister(mFragmentIdlingResource);
        }

        mProgressbarIdlingResource = null;
        mFragmentIdlingResource = null;
    }
}

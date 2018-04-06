package org.openhab.habdroid;

import android.support.annotation.NonNull;
import android.support.test.espresso.IdlingResource;
import android.view.View;

public class OpenHABProgressbarIdlingResource implements IdlingResource {
    private String mName;
    private View mProgressBar;
    private ResourceCallback mCallback;

    public OpenHABProgressbarIdlingResource(@NonNull String name, @NonNull View progressBar) {
        mName = name;
        mProgressBar = progressBar;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isIdleNow() {
        boolean idle = mProgressBar.getVisibility() == View.INVISIBLE;
        if (idle && mCallback != null)
            mCallback.onTransitionToIdle();

        return idle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        mCallback = callback;
    }
}

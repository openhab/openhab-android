package org.openhab.habdroid;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.test.espresso.IdlingResource;

public class ProgressbarIdlingResource implements IdlingResource {
    private String mName;
    private View mProgressBar;
    private ResourceCallback mCallback;

    public ProgressbarIdlingResource(@NonNull String name, @NonNull View progressBar) {
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
        if (idle && mCallback != null) {
            mCallback.onTransitionToIdle();
        }

        return idle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        mCallback = callback;
    }
}

package org.openhab.habdroid;

import android.support.annotation.NonNull;
import android.support.test.espresso.IdlingResource;
import android.view.View;

public class OpenHABProgressbarIdlingResource implements IdlingResource {
    private String name;
    private View progressBar;
    private ResourceCallback callback;

    public OpenHABProgressbarIdlingResource(@NonNull String name, @NonNull View progressBar) {
        this.name = name;
        this.progressBar = progressBar;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean isIdleNow() {
        boolean idle = progressBar.getVisibility() == View.INVISIBLE;
        if (idle && callback != null)
            callback.onTransitionToIdle();

        return idle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.callback = callback;
    }
}

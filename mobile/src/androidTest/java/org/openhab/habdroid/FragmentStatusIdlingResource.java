package org.openhab.habdroid;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.espresso.IdlingResource;

import org.openhab.habdroid.ui.WidgetListFragment;
import org.openhab.habdroid.ui.activity.ContentController;

public class FragmentStatusIdlingResource implements IdlingResource {
    private final String mName;
    private final FragmentManager mFm;
    private IdlingResource.ResourceCallback mCallback;

    public FragmentStatusIdlingResource(@NonNull String name, @NonNull FragmentManager fm) {
        mName = name;
        mFm = fm;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isIdleNow() {
        boolean idle = !hasBusyFragments();
        if (idle && mCallback != null) {
            mCallback.onTransitionToIdle();
        }

        return idle;
    }

    private boolean hasBusyFragments() {
        if (mFm.isDestroyed()) {
            return false;
        }
        mFm.executePendingTransactions();
        for (Fragment f : mFm.getFragments()) {
            if (f instanceof ContentController.ProgressFragment) {
                return true;
            }
            if (f instanceof WidgetListFragment) {
                if (((WidgetListFragment) f).mRecyclerView.hasPendingAdapterUpdates()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        mCallback = callback;
    }
}

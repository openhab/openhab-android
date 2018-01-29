/*
 * Copyright (c) 2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.activity;

import android.support.annotation.AnimRes;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;

@SuppressWarnings("unused") // instantiated via reflection
public class FragmentControllerOnePane extends FragmentController {
    public FragmentControllerOnePane(OpenHABMainActivity activity) {
        super(activity);
    }

    @Override
    protected void updateFragmentState(FragmentUpdateReason reason) {
        Fragment currentFragment = mFm.findFragmentById(R.id.content);
        Fragment fragment = mNoConnectionFragment != null
                ? mNoConnectionFragment
                : mPageStack.empty() ? mSitemapFragment : mPageStack.peek().second;
        if (fragment != null) {
            mFm.beginTransaction()
                    .setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
                    .replace(R.id.content, fragment)
                    .commit();
        } else if (currentFragment != null) {
            mFm.beginTransaction()
                    .remove(currentFragment)
                    .commit();
        }
    }

    @Override
    protected void showTemporaryPage(Fragment page) {
        mFm.beginTransaction()
                .replace(R.id.content, page)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public String getCurrentTitle() {
        return mPageStack.empty() ? null : mPageStack.peek().second.getTitle();
    }

    @Override
    public @LayoutRes int getContentLayoutResource() {
        return R.layout.content_onepane;
    }
}

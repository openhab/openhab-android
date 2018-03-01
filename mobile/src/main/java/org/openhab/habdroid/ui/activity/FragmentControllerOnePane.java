/*
 * Copyright (c) 2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.activity;

import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.view.ViewStub;

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
        final Fragment fragment;
        if (mNoConnectionFragment != null) {
            fragment = mNoConnectionFragment;
        } else if (!mPageStack.empty()) {
            fragment = mPageStack.peek().second;
        } else if (mSitemapFragment != null) {
            fragment = mSitemapFragment;
        } else {
            fragment = mDefaultProgressFragment;
        }

        mFm.beginTransaction()
                .setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
                .replace(R.id.content, fragment)
                .commit();
    }

    @Override
    protected void showTemporaryPage(Fragment page, CharSequence title) {
        mFm.beginTransaction()
                .replace(R.id.content, page)
                .setBreadCrumbTitle(title)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public CharSequence getCurrentTitle() {
        int count = mFm.getBackStackEntryCount();
        if (count > 0) {
            return mFm.getBackStackEntryAt(count - 1).getBreadCrumbTitle();
        }
        return mPageStack.empty() ? null : mPageStack.peek().second.getTitle();
    }

    @Override
    public void inflateViews(ViewStub stub) {
        stub.setLayoutResource(R.layout.content_onepane);
        stub.inflate();
    }
}

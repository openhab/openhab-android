/*
 * Copyright (c) 2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.activity;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.ViewStub;

import org.openhab.habdroid.R;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.ui.OpenHABWidgetListFragment;

@SuppressWarnings("unused") // instantiated via reflection
public class ContentControllerOnePane extends ContentController {
    public ContentControllerOnePane(OpenHABMainActivity activity) {
        super(activity);
    }

    @Override
    protected void executeStateUpdate(FragmentUpdateReason reason, boolean allowStateLoss) {
        Fragment currentFragment = mFm.findFragmentById(R.id.content);
        Fragment fragment = getOverridingFragment();
        if (fragment == null && !mPageStack.isEmpty()) {
            fragment = mPageStack.peek().second;
        }
        if (fragment == null) {
            fragment = mSitemapFragment;
        }

        FragmentTransaction ft = mFm.beginTransaction()
                .setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason))
                .replace(R.id.content, fragment != null ? fragment : mDefaultProgressFragment);
        if (allowStateLoss) {
            ft.commitAllowingStateLoss();
        } else {
            ft.commit();
        }
    }

    @Override
    protected OpenHABWidgetListFragment getFragmentForTitle() {
        return mPageStack.empty() ? mSitemapFragment : mPageStack.peek().second;
    }

    @Override
    public void inflateViews(ViewStub stub) {
        stub.setLayoutResource(R.layout.content_onepane);
        stub.inflate();
    }
}

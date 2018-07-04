/*
 * Copyright (c) 2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Pair;
import android.view.View;
import android.view.ViewStub;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.ui.OpenHABWidgetListFragment;

@SuppressWarnings("unused") // instantiated via reflection
public class ContentControllerTwoPane extends ContentController {
    private View mRightContentView;

    public ContentControllerTwoPane(OpenHABMainActivity activity) {
        super(activity);
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        boolean rightPaneVisible = mFm.findFragmentById(R.id.content_right) != null;
        mRightContentView.setVisibility(rightPaneVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void executeStateUpdate(FragmentUpdateReason reason, boolean allowStateLoss) {
        Fragment leftFragment = getOverridingFragment();
        final OpenHABWidgetListFragment rightFragment;
        final Pair<OpenHABLinkedPage, OpenHABWidgetListFragment> rightPair;

        if (leftFragment != null) {
            rightFragment = null;
            rightPair = null;
        } else if (mSitemapFragment != null) {
            rightPair = mPageStack.empty() ? null : mPageStack.peek();
            leftFragment = mPageStack.size() > 1
                    ? mPageStack.get(mPageStack.size() - 2).second : mSitemapFragment;
            rightFragment = rightPair != null ? rightPair.second : null;
        } else {
            leftFragment = mDefaultProgressFragment;
            rightFragment = null;
            rightPair = null;
        }

        Fragment currentLeftFragment = mFm.findFragmentById(R.id.content_left);
        Fragment currentRightFragment = mFm.findFragmentById(R.id.content_right);

        FragmentTransaction removeTransaction = mFm.beginTransaction();
        boolean needRemove = false;
        if (currentLeftFragment != null && currentLeftFragment != leftFragment) {
            removeTransaction.remove(currentLeftFragment);
            needRemove = true;
        }
        if (currentRightFragment != null && currentRightFragment != rightFragment) {
            removeTransaction.remove(currentRightFragment);
            needRemove = true;
        }
        if (needRemove) {
            if (allowStateLoss) {
                removeTransaction.commitNowAllowingStateLoss();
            } else {
                removeTransaction.commitNow();
            }
        }

        FragmentTransaction ft = mFm.beginTransaction();
        ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        if (leftFragment != null) {
            ft.setCustomAnimations(determineEnterAnim(reason), determineExitAnim(reason));
            ft.replace(R.id.content_left, leftFragment);
            if (leftFragment instanceof OpenHABWidgetListFragment) {
                OpenHABWidgetListFragment llf = (OpenHABWidgetListFragment) leftFragment;
                llf.setHighlightedPageLink(rightPair != null ? rightPair.first.link() : null);
            }
        }
        if (rightFragment != null) {
            ft.setCustomAnimations(0, 0);
            ft.replace(R.id.content_right, rightFragment);
            rightFragment.setHighlightedPageLink(null);
        }
        if (allowStateLoss) {
            ft.commitAllowingStateLoss();
        } else {
            ft.commit();
        }

        mRightContentView.setVisibility(rightFragment != null ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openPage(OpenHABLinkedPage page, OpenHABWidgetListFragment source) {
        Fragment currentLeftFragment = mFm.findFragmentById(R.id.content_left);
        if (source == currentLeftFragment && !mPageStack.empty()) {
            mPageStack.pop();
        }
        super.openPage(page, source);
    }

    @Override
    protected OpenHABWidgetListFragment getFragmentForTitle() {
        return mPageStack.size() > 1
                ? mPageStack.get(mPageStack.size() - 2).second
                : mSitemapFragment;
    }

    @Override
    public void inflateViews(ViewStub stub) {
        stub.setLayoutResource(R.layout.content_twopane);
        View view = stub.inflate();
        mRightContentView = view.findViewById(R.id.content_right);
        mRightContentView.setVisibility(View.GONE);
    }
}

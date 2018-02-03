/*
 * Copyright (c) 2018, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.AnimRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.ui.OpenHABMainActivity;
import org.openhab.habdroid.ui.OpenHABNotificationFragment;
import org.openhab.habdroid.ui.OpenHABPreferencesActivity;
import org.openhab.habdroid.ui.OpenHABWidgetListFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public abstract class FragmentController {
    private final OpenHABMainActivity mActivity;
    protected final FragmentManager mFm;
    protected Fragment mNoConnectionFragment;
    protected OpenHABSitemap mCurrentSitemap;
    protected OpenHABWidgetListFragment mSitemapFragment;
    protected final Stack<Pair<OpenHABLinkedPage, OpenHABWidgetListFragment>> mPageStack = new Stack<>();
    private Set<String> mPendingDataLoadUrls = new HashSet<>();
    private PageConnectionHolderFragment mConnectionFragment;

    protected FragmentController(OpenHABMainActivity activity) {
        mActivity = activity;
        mFm = activity.getSupportFragmentManager();

        mConnectionFragment = (PageConnectionHolderFragment) mFm.findFragmentByTag("connections");
        if (mConnectionFragment == null) {
            mConnectionFragment = new PageConnectionHolderFragment();
            mFm.beginTransaction().add(mConnectionFragment, "connections").commit();
        }
    }

    public void onSaveInstanceState(Bundle state) {
        ArrayList<OpenHABLinkedPage> pages = new ArrayList<>();
        for (Pair<OpenHABLinkedPage, OpenHABWidgetListFragment> item : mPageStack) {
            pages.add(item.first);
            if (item.second.isAdded()) {
                mFm.putFragment(state, "pageFragment-" + item.first.getLink(), item.second);
            }
        }
        state.putParcelable("controllerSitemap", mCurrentSitemap);
        if (mSitemapFragment != null && mSitemapFragment.isAdded()) {
            mFm.putFragment(state, "sitemapFragment", mSitemapFragment);
        }
        state.putParcelableArrayList("controllerPages", pages);
    }

    public void onRestoreInstanceState(Bundle state) {
        mCurrentSitemap = state.getParcelable("controllerSitemap");
        if (mCurrentSitemap != null) {
            mSitemapFragment = (OpenHABWidgetListFragment)
                    mFm.getFragment(state, "sitemapFragment");
            if (mSitemapFragment == null) {
                mSitemapFragment = makeSitemapFragment(mCurrentSitemap);
            }
        }

        ArrayList<OpenHABLinkedPage> oldStack = state.getParcelableArrayList("controllerPages");
        mPageStack.clear();
        for (OpenHABLinkedPage page : oldStack) {
            OpenHABWidgetListFragment f = (OpenHABWidgetListFragment)
                    mFm.getFragment(state, "pageFragment-" + page.getLink());
            mPageStack.add(Pair.create(page, f != null ? f : makePageFragment(page)));
        }
    }

    public void openSitemap(OpenHABSitemap sitemap) {
        mCurrentSitemap = sitemap;
        mSitemapFragment = makeSitemapFragment(sitemap);
        mPageStack.clear();
        updateFragmentState();
        updateConnectionState();
    }

    public void openPage(OpenHABLinkedPage page, OpenHABWidgetListFragment source) {
        mPageStack.push(Pair.create(page, makePageFragment(page)));
        mPendingDataLoadUrls.add(page.getLink());
        // no fragment update yet; fragment state will be updated when data arrives
        mActivity.setProgressIndicatorVisible(true);
        updateConnectionState();
    }

    public final void openPage(String url) {
        int toPop = -1;
        for (int i = 0; i < mPageStack.size(); i++) {
            if (mPageStack.get(i).first.getLink().equals(url)) {
                // page is already present
                toPop = mPageStack.size() - i - 1;
                break;
            }
        }
        if (toPop >= 0) {
            while (toPop-- > 0) {
                mPageStack.pop();
            }
            updateFragmentState();
        } else {
            // we didn't find it
            showTemporaryPage(OpenHABWidgetListFragment.withPage(url, null));
        }
    }

    public void indicateNoNetwork(String message) {
        resetState();
        mNoConnectionFragment = NoNetworkFragment.newInstance(message);
        updateFragmentState();
    }

    public void indicateMissingConfiguration() {
        resetState();
        mNoConnectionFragment = MissingConfigurationFragment.newInstance(mActivity);
        updateFragmentState();
    }

    public void updateConnection(Connection connection, String progressMessage) {
        // XXX: show pro
    }

    public final void openNotifications() {
        showTemporaryPage(OpenHABNotificationFragment.newInstance());
    }

    public void onPageUpdated(String pageUrl, String pageTitle, List<OpenHABWidget> widgets) {
        if (mSitemapFragment != null && pageUrl.equals(mSitemapFragment.getDisplayPageUrl())) {
            mSitemapFragment.update(pageTitle, widgets);
        } else {
            for (Pair<OpenHABLinkedPage, OpenHABWidgetListFragment> item : mPageStack) {
                if (pageUrl.equals(item.second.getDisplayPageUrl())) {
                    item.second.update(pageTitle, widgets);
                    break;
                }
            }
        }
        if (mPendingDataLoadUrls.remove(pageUrl) && mPendingDataLoadUrls.isEmpty()) {
            mActivity.setProgressIndicatorVisible(false);
            updateFragmentState(FragmentUpdateReason.PAGE_ENTER);
        }
    }

    public void triggerPageUpdate(String pageUrl, boolean forceReload) {
        mConnectionFragment.triggerUpdate(pageUrl, forceReload);
    }

    public void initViews(View contentView) {}
    public void updateFragmentState() {
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
        updateConnectionState();
    }

    public abstract String getCurrentTitle();
    public abstract @LayoutRes int getContentLayoutResource();
    protected abstract void updateFragmentState(FragmentUpdateReason reason);
    protected abstract void showTemporaryPage(Fragment page);

    public boolean canGoBack() {
        return !mPageStack.empty() || mFm.getBackStackEntryCount() > 0;
    }

    public boolean goBack() {
        if (mFm.getBackStackEntryCount() > 0) {
            mFm.popBackStackImmediate();
            return true;
        }
        if (!mPageStack.empty()) {
            mPageStack.pop();
            updateFragmentState(FragmentUpdateReason.BACK_NAVIGATION);
            updateConnectionState();
            return true;
        }
        return false;
    }

    private void resetState() {
        mCurrentSitemap = null;
        mSitemapFragment = null;
        mPageStack.clear();
    }

    protected void updateConnectionState() {
        List<String> pageUrls = new ArrayList<>();
        if (mSitemapFragment != null) {
            pageUrls.add(mSitemapFragment.getDisplayPageUrl());
        }
        for (Pair<OpenHABLinkedPage, OpenHABWidgetListFragment> item : mPageStack) {
            pageUrls.add(item.second.getDisplayPageUrl());
        }
        mConnectionFragment.updateActiveConnections(pageUrls);
    }

    private OpenHABWidgetListFragment makeSitemapFragment(OpenHABSitemap sitemap) {
        return OpenHABWidgetListFragment.withPage(sitemap.getHomepageLink(), sitemap.getLabel());
    }

    private OpenHABWidgetListFragment makePageFragment(OpenHABLinkedPage page) {
        return OpenHABWidgetListFragment.withPage(page.getLink(), page.getTitle());
    }

    protected enum FragmentUpdateReason {
        PAGE_ENTER,
        BACK_NAVIGATION,
        PAGE_UPDATE
    }


    protected static @AnimRes int determineEnterAnim(FragmentUpdateReason reason) {
        switch (reason) {
            case PAGE_ENTER: return R.anim.slide_in_right;
            case BACK_NAVIGATION: return R.anim.slide_in_left;
            default: return 0;
        }
    }

    protected static @AnimRes int determineExitAnim(FragmentUpdateReason reason) {
        switch (reason) {
            case PAGE_ENTER: return R.anim.slide_out_left;
            case BACK_NAVIGATION: return R.anim.slide_out_right;
            default: return 0;
        }
    }

    protected static int determineTransition(FragmentUpdateReason reason) {
        switch (reason) {
            case PAGE_ENTER: return FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
            case BACK_NAVIGATION: return FragmentTransaction.TRANSIT_FRAGMENT_CLOSE;
            default: return FragmentTransaction.TRANSIT_FRAGMENT_FADE;
        }
    }

    public static class NoNetworkFragment extends StatusFragment {
        public static NoNetworkFragment newInstance(String message) {
            NoNetworkFragment f = new NoNetworkFragment();
            f.setArguments(buildArgs(message, R.drawable.ic_signal_cellular_off_black_24dp,
                    R.string.try_again_button));
            return f;
        }

        @Override
        public void onClick(View view) {
            ConnectionFactory.restartNetworkCheck();
            getActivity().recreate();
        }
    }

    public static class MissingConfigurationFragment extends StatusFragment {
        public static MissingConfigurationFragment newInstance(Context context) {
            MissingConfigurationFragment f = new MissingConfigurationFragment();
            f.setArguments(buildArgs(context.getString(R.string.configuration_missing),
                    R.drawable.ic_openhab_appicon_24dp, /* FIXME? */
                    R.string.go_to_settings_button));
            return f;
        }

        @Override
        public void onClick(View view) {
            Intent preferencesIntent = new Intent(getActivity(), OpenHABPreferencesActivity.class);
            preferencesIntent.putExtra(OpenHABPreferencesActivity.EXTRA_INITIAL_MESSAGE,
                    getString(R.string.error_no_url));

            TaskStackBuilder.create(getActivity())
                    .addNextIntentWithParentStack(preferencesIntent)
                    .startActivities();
        }
    }

    private abstract static class StatusFragment extends Fragment implements View.OnClickListener {
        protected static Bundle buildArgs(String message, @DrawableRes int drawableResId,
                @StringRes int buttonTextResId) {
            Bundle args = new Bundle();
            args.putString("message", message);
            args.putInt("drawable", drawableResId);
            args.putInt("buttontext", buttonTextResId);
            return args;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            Bundle arguments = getArguments();

            View view = inflater.inflate(R.layout.fragment_status, container, false);

            TextView descriptionText = view.findViewById(R.id.description);
            String message = arguments.getString("message");
            if (!TextUtils.isEmpty(message)) {
                descriptionText.setText(message);
            } else {
                descriptionText.setVisibility(View.GONE);
            }

            final ImageView watermark = view.findViewById(R.id.image);

            Drawable errorImage = ContextCompat.getDrawable(getActivity(),
                    arguments.getInt("drawable"));
            errorImage.setColorFilter(
                    ContextCompat.getColor(getActivity(), R.color.empty_list_text_color),
                    PorterDuff.Mode.SRC_IN);
            watermark.setImageDrawable(errorImage);

            final Button button = view.findViewById(R.id.button);
            button.setText(arguments.getInt("buttontext"));
            button.setOnClickListener(this);

            return view;
        }
    }
}

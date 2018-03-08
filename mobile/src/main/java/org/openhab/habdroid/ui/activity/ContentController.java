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
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.AnimRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Controller class for the content area of {@link OpenHABMainActivity}
 *
 * It manages the stack of widget lists shown, and shows error UI if needed.
 * The layout of the content area is up to the respective subclasses.
 */
public abstract class ContentController implements PageConnectionHolderFragment.ParentCallback {
    private static final String TAG = ContentController.class.getSimpleName();

    private final OpenHABMainActivity mActivity;
    protected final FragmentManager mFm;

    protected Fragment mNoConnectionFragment;
    protected Fragment mDefaultProgressFragment;
    private PageConnectionHolderFragment mConnectionFragment;
    private Fragment mTemporaryPage;

    protected OpenHABSitemap mCurrentSitemap;
    protected OpenHABWidgetListFragment mSitemapFragment;
    protected final Stack<Pair<OpenHABLinkedPage, OpenHABWidgetListFragment>> mPageStack = new Stack<>();
    private Set<String> mPendingDataLoadUrls = new HashSet<>();

    protected ContentController(OpenHABMainActivity activity) {
        mActivity = activity;
        mFm = activity.getSupportFragmentManager();

        mConnectionFragment = (PageConnectionHolderFragment) mFm.findFragmentByTag("connections");
        if (mConnectionFragment == null) {
            mConnectionFragment = new PageConnectionHolderFragment();
            mFm.beginTransaction().add(mConnectionFragment, "connections").commit();
        }
        mDefaultProgressFragment = ProgressFragment.newInstance(null, false);
        mConnectionFragment.setCallback(this);
    }

    /**
     * Saves the controller's instance state
     * To be called from the onSaveInstanceState callback of the activity
     *
     * @param state Bundle to save state into
     */
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
        if (mDefaultProgressFragment.isAdded()) {
            mFm.putFragment(state, "progressFragment", mDefaultProgressFragment);
        }
        state.putParcelableArrayList("controllerPages", pages);
        if (mTemporaryPage != null) {
            mFm.putFragment(state, "temporaryPage", mTemporaryPage);
        }
    }

    /**
     * Restore instance state previously saved by onSaveInstanceState
     * To be called from the onRestoreInstanceState or onCreate callbacks of the activity
     *
     * @param state Bundle including previously saved state
     */
    public void onRestoreInstanceState(Bundle state) {
        mCurrentSitemap = state.getParcelable("controllerSitemap");
        if (mCurrentSitemap != null) {
            mSitemapFragment = (OpenHABWidgetListFragment)
                    mFm.getFragment(state, "sitemapFragment");
            if (mSitemapFragment == null) {
                mSitemapFragment = makeSitemapFragment(mCurrentSitemap);
            }
        }
        Fragment progressFragment = mFm.getFragment(state, "progressFragment");
        if (progressFragment != null) {
            mDefaultProgressFragment = progressFragment;
        }

        ArrayList<OpenHABLinkedPage> oldStack = state.getParcelableArrayList("controllerPages");
        mPageStack.clear();
        for (OpenHABLinkedPage page : oldStack) {
            OpenHABWidgetListFragment f = (OpenHABWidgetListFragment)
                    mFm.getFragment(state, "pageFragment-" + page.getLink());
            mPageStack.add(Pair.create(page, f != null ? f : makePageFragment(page)));
        }
        mTemporaryPage = mFm.getFragment(state, "temporaryPage");
    }

    /**
     * Show contents of a sitemap
     * Sets up UI to show the sitemap's contents
     *
     * @param sitemap Sitemap to show
     */
    public void openSitemap(OpenHABSitemap sitemap) {
        Log.d(TAG, "Opening sitemap " + sitemap);
        mCurrentSitemap = sitemap;
        // First clear the old fragment stack to show the progress spinner...
        mPageStack.clear();
        mSitemapFragment = null;
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
        // ...then create the new sitemap fragment and trigger data loading.
        mSitemapFragment = makeSitemapFragment(sitemap);
        handleNewWidgetFragment(mSitemapFragment);
    }

    /**
     * Follow a link in a sitemap page
     * Sets up UI to show the contents of the given page
     *
     * @param page Page link to follow
     * @param source Fragment this action was triggered from
     */
    public void openPage(OpenHABLinkedPage page, OpenHABWidgetListFragment source) {
        Log.d(TAG, "Opening page " + page);
        OpenHABWidgetListFragment f = makePageFragment(page);
        while (!mPageStack.isEmpty() && mPageStack.peek().second != source) {
            mPageStack.pop();
        }
        mPageStack.push(Pair.create(page, f));
        handleNewWidgetFragment(f);
        mActivity.setProgressIndicatorVisible(true);
    }

    /**
     * Follow a sitemap page link via URL
     * If a page with the given URL is already present in the back stack,
     * that page is brought to the front; otherwise a temporary page with showing
     * the contents of the linked page is opened.
     *
     * @param url URL to follow
     */
    public final void openPage(String url) {
        int toPop = -1;
        for (int i = 0; i < mPageStack.size(); i++) {
            if (mPageStack.get(i).first.getLink().equals(url)) {
                // page is already present
                toPop = mPageStack.size() - i - 1;
                break;
            }
        }
        Log.d(TAG, "Opening page " + url + " (pop count " + toPop + ")");
        if (toPop >= 0) {
            while (toPop-- > 0) {
                mPageStack.pop();
            }
            updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
            updateConnectionState();
            mActivity.updateTitle();
        } else {
            // we didn't find it
            showTemporaryPage(OpenHABWidgetListFragment.withPage(url, null));
        }
    }

    /**
     * Indicate to the user that no network connectivity is present
     *
     * @param message Error message to show
     */
    public void indicateNoNetwork(CharSequence message) {
        Log.d(TAG, "Indicate no network (message " + message + ")");
        resetState();
        mNoConnectionFragment = NoNetworkFragment.newInstance(message);
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
        mActivity.updateTitle();
    }

    /**
     * Indicate to the user that server configuration is missing
     */
    public void indicateMissingConfiguration() {
        Log.d(TAG, "Indicate missing configuration");
        resetState();
        mNoConnectionFragment = MissingConfigurationFragment.newInstance(mActivity);
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
        mActivity.updateTitle();
    }

    /**
     * Indicate to the user that there was a failure in talking to the server
     *
     * @param message Error message to show
     */
    public void indicateServerCommunicationFailure(CharSequence message) {
        Log.d(TAG, "Indicate server failure (message " + message + ")");
        mNoConnectionFragment = CommunicationFailureFragment.newInstance(message);
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
        mActivity.updateTitle();
    }

    /**
     * Clear the error previously set by {@link #indicateServerCommunicationFailure}
     */
    public void clearServerCommunicationFailure() {
        if (mNoConnectionFragment instanceof CommunicationFailureFragment) {
            mNoConnectionFragment = null;
            updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
            mActivity.updateTitle();
        }
    }

    /**
     * Update the used connection.
     * To be called when the available connection changes.
     *
     * @param connection New connection to use; might be null if none is currently available
     * @param progressMessage Message to show to the user if no connection is available
     */
    public void updateConnection(Connection connection, CharSequence progressMessage) {
        Log.d(TAG, "Update to connection " + connection + " (message " + progressMessage + ")");
        if (connection == null) {
            mNoConnectionFragment = ProgressFragment.newInstance(progressMessage,
                    progressMessage != null);
        } else {
            mNoConnectionFragment = null;
        }
        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
        updateConnectionState();
    }

    /**
     * Open a temporary page showing the notification list
     */
    public final void openNotifications() {
        showTemporaryPage(OpenHABNotificationFragment.newInstance());
    }

    /**
     * Recreate all UI state
     * To be called from the activity's onCreate callback if the used controller changes
     */
    public void recreateFragmentState() {
        FragmentTransaction ft = mFm.beginTransaction();
        for (Fragment f : mFm.getFragments()) {
            if (!f.getRetainInstance()) {
                ft.remove(f);
            }
        }
        ft.commitNow();

        updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
    }

    /**
     * Inflate controller views
     * To be called after activity content view inflation
     *
     * @param stub View stub to inflate controller views into
     */
    public abstract void inflateViews(ViewStub stub);

    /**
     * Ask the connection controller to deliver content updates for a given page
     *
     * @param pageUrl URL of the content page
     * @param forceReload Whether to discard previously cached state
     */
    public void triggerPageUpdate(String pageUrl, boolean forceReload) {
        mConnectionFragment.triggerUpdate(pageUrl, forceReload);
    }

    /**
     * Get title describing current UI state
     *
     * @return Title to show in action bar, or null if none can be determined
     */
    public CharSequence getCurrentTitle() {
        if (mNoConnectionFragment != null) {
            return null;
        } else if (mTemporaryPage != null) {
            if (mTemporaryPage instanceof OpenHABNotificationFragment) {
                return mActivity.getString(R.string.app_notifications);
            } else if (mTemporaryPage instanceof OpenHABWidgetListFragment) {
                return ((OpenHABWidgetListFragment) mTemporaryPage).getTitle();
            }
            return null;
        } else {
            OpenHABWidgetListFragment f = getFragmentForTitle();
            return f != null ? f.getTitle() : null;
        }
    }

    /**
     * Checks whether the controller currently can consume the back key
     *
     * @return true if back key can be consumed, false otherwise
     */
    public boolean canGoBack() {
        return mTemporaryPage != null || !mPageStack.empty();
    }

    /**
     * Consumes the back key
     * To be called from activity onBackKeyPressed callback
     *
     * @return true if back key was consumed, false otherwise
     */
    public boolean goBack() {
        if (mTemporaryPage != null) {
            mTemporaryPage = null;
            mActivity.updateTitle();
            updateFragmentState(FragmentUpdateReason.PAGE_UPDATE);
            updateConnectionState();
            return true;
        }
        if (!mPageStack.empty()) {
            mPageStack.pop();
            mActivity.updateTitle();
            updateFragmentState(FragmentUpdateReason.BACK_NAVIGATION);
            updateConnectionState();
            return true;
        }
        return false;
    }

    @Override
    public boolean serverReturnsJson() {
        return mActivity.getOpenHABVersion() != 1;
    }

    @Override
    public String getIconFormat() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        return prefs.getString("iconFormatType","PNG");
    }

    @Override
    public void onPageUpdated(String pageUrl, String pageTitle, List<OpenHABWidget> widgets) {
        Log.d(TAG, "Got update for URL " + pageUrl + ", pending " + mPendingDataLoadUrls);
        for (OpenHABWidgetListFragment f : collectWidgetFragments()) {
            if (pageUrl.equals(f.getDisplayPageUrl())) {
                f.update(pageTitle, widgets);
                break;
            }
        }
        if (mPendingDataLoadUrls.remove(pageUrl) && mPendingDataLoadUrls.isEmpty()) {
            mActivity.setProgressIndicatorVisible(false);
            mActivity.updateTitle();
            updateFragmentState(mPageStack.isEmpty()
                    ? FragmentUpdateReason.PAGE_UPDATE : FragmentUpdateReason.PAGE_ENTER);
        }
    }

    @Override
    public void onWidgetUpdated(OpenHABWidget widget) {
        for (OpenHABWidgetListFragment f : collectWidgetFragments()) {
            if (f.onWidgetUpdated(widget)) {
                break;
            }
        }
    }

    protected abstract void updateFragmentState(FragmentUpdateReason reason);
    protected abstract OpenHABWidgetListFragment getFragmentForTitle();

    private void handleNewWidgetFragment(OpenHABWidgetListFragment f) {
        mPendingDataLoadUrls.add(f.getDisplayPageUrl());
        // no fragment update yet; fragment state will be updated when data arrives
        updateConnectionState();
    }

    private void showTemporaryPage(Fragment page) {
        mTemporaryPage = page;
        updateFragmentState(FragmentUpdateReason.TEMPORARY_PAGE);
        updateConnectionState();
        mActivity.updateTitle();
    }

    protected Fragment getOverridingFragment() {
        if (mNoConnectionFragment != null) {
            return mNoConnectionFragment;
        }
        if (mTemporaryPage != null) {
            return mTemporaryPage;
        }
        return null;
    }

    protected void updateConnectionState() {
        List<String> pageUrls = new ArrayList<>();
        for (OpenHABWidgetListFragment f : collectWidgetFragments()) {
            pageUrls.add(f.getDisplayPageUrl());
        }
        Iterator<String> pendingIter = mPendingDataLoadUrls.iterator();
        while (pendingIter.hasNext()) {
            if (!pageUrls.contains(pendingIter.next())) {
                pendingIter.remove();
            }
        }
        mConnectionFragment.updateActiveConnections(pageUrls, mActivity.getConnection());
    }

    private void resetState() {
        mCurrentSitemap = null;
        mSitemapFragment = null;
        mPageStack.clear();
        updateConnectionState();
    }

    private List<OpenHABWidgetListFragment> collectWidgetFragments() {
        List<OpenHABWidgetListFragment> result = new ArrayList<>();
        if (mSitemapFragment != null) {
            result.add(mSitemapFragment);
        }
        for (Pair<OpenHABLinkedPage, OpenHABWidgetListFragment> item : mPageStack) {
            result.add(item.second);
        }
        if (mTemporaryPage instanceof OpenHABWidgetListFragment) {
            result.add((OpenHABWidgetListFragment) mTemporaryPage);
        }
        return result;
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
        TEMPORARY_PAGE,
        PAGE_UPDATE
    }

    protected static @AnimRes int determineEnterAnim(FragmentUpdateReason reason) {
        switch (reason) {
            case PAGE_ENTER: return R.anim.slide_in_right;
            case TEMPORARY_PAGE: return R.anim.slide_in_bottom;
            case BACK_NAVIGATION: return R.anim.slide_in_left;
            default: return 0;
        }
    }

    protected static @AnimRes int determineExitAnim(FragmentUpdateReason reason) {
        switch (reason) {
            case PAGE_ENTER: return R.anim.slide_out_left;
            case TEMPORARY_PAGE: return R.anim.slide_out_bottom;
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

    public static class CommunicationFailureFragment extends StatusFragment {
        public static CommunicationFailureFragment newInstance(CharSequence message) {
            CommunicationFailureFragment f = new CommunicationFailureFragment();
            f.setArguments(buildArgs(message, R.drawable.ic_openhab_appicon_24dp /* FIXME */,
                    R.string.try_again_button, false));
            return f;
        }

        @Override
        public void onClick(View view) {
            ((OpenHABMainActivity) getActivity()).retryServerPropertyQuery();
        }
    }

    public static class ProgressFragment extends StatusFragment {
        public static ProgressFragment newInstance(CharSequence message, boolean showImage) {
            ProgressFragment f = new ProgressFragment();
            f.setArguments(buildArgs(message,
                    showImage ? R.drawable.ic_openhab_appicon_24dp : 0,
                    0, true));
            return f;
        }

        @Override
        public void onClick(View view) {
            // no-op, we don't show the button
        }
    }

    public static class NoNetworkFragment extends StatusFragment {
        public static NoNetworkFragment newInstance(CharSequence message) {
            NoNetworkFragment f = new NoNetworkFragment();
            f.setArguments(buildArgs(message, R.drawable.ic_signal_cellular_off_black_24dp,
                    R.string.try_again_button, false));
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
                    R.string.go_to_settings_button, false));
            return f;
        }

        @Override
        public void onClick(View view) {
            Intent preferencesIntent = new Intent(getActivity(), OpenHABPreferencesActivity.class);
            TaskStackBuilder.create(getActivity())
                    .addNextIntentWithParentStack(preferencesIntent)
                    .startActivities();
        }
    }

    private abstract static class StatusFragment extends Fragment implements View.OnClickListener {
        protected static Bundle buildArgs(CharSequence message, @DrawableRes int drawableResId,
                @StringRes int buttonTextResId, boolean showProgress) {
            Bundle args = new Bundle();
            args.putCharSequence("message", message);
            args.putInt("drawable", drawableResId);
            args.putInt("buttontext", buttonTextResId);
            args.putBoolean("progress", showProgress);
            return args;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            Bundle arguments = getArguments();

            View view = inflater.inflate(R.layout.fragment_status, container, false);

            TextView descriptionText = view.findViewById(R.id.description);
            CharSequence message = arguments.getCharSequence("message");
            if (!TextUtils.isEmpty(message)) {
                descriptionText.setText(message);
            } else {
                descriptionText.setVisibility(View.GONE);
            }

            view.findViewById(R.id.progress).setVisibility(
                    arguments.getBoolean("progress") ? View.VISIBLE : View.GONE);

            final ImageView watermark = view.findViewById(R.id.image);
            @DrawableRes int drawableResId = arguments.getInt("drawable");
            if (drawableResId != 0) {
                Drawable drawable = ContextCompat.getDrawable(getActivity(), drawableResId);
                drawable.setColorFilter(
                        ContextCompat.getColor(getActivity(), R.color.empty_list_text_color),
                        PorterDuff.Mode.SRC_IN);
                watermark.setImageDrawable(drawable);
            } else {
                watermark.setVisibility(View.GONE);
            }

            final Button button = view.findViewById(R.id.button);
            int buttonTextResId = arguments.getInt("buttontext");
            if (buttonTextResId != 0) {
                button.setText(buttonTextResId);
                button.setOnClickListener(this);
            } else {
                button.setVisibility(View.GONE);
            }

            return view;
        }
    }
}

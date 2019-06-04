/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import es.dmoral.toasty.Toasty;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.LabeledValue;
import org.openhab.habdroid.model.LinkedPage;
import org.openhab.habdroid.model.Widget;
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.SuggestedCommandsFactory;
import org.openhab.habdroid.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

public class WidgetListFragment extends Fragment
        implements WidgetAdapter.ItemClickListener {
    private static final String TAG = WidgetListFragment.class.getSimpleName();

    @VisibleForTesting
    public RecyclerView mRecyclerView;
    private LinearLayout mEmptyPageView;
    private LinearLayoutManager mLayoutManager;
    private WidgetAdapter mAdapter;
    // Url of current sitemap page displayed
    private String mPageUrl;
    // parent activity
    private MainActivity mActivity;
    private String mTitle;
    private RecyclerViewSwipeRefreshLayout mRefreshLayout;
    private String mHighlightedPageLink;
    private SuggestedCommandsFactory mSuggestedCommandsFactory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (mTitle == null) {
            if (savedInstanceState != null) {
                mTitle = savedInstanceState.getString("title");
            } else {
                mTitle = args.getString("title");
            }
        }
        mPageUrl = args.getString("displayPageUrl");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated() " + mPageUrl);
        mActivity = (MainActivity) getActivity();

        mAdapter = new WidgetAdapter(mActivity, mActivity.getConnection(), this);

        mLayoutManager = new LinearLayoutManager(mActivity);
        mLayoutManager.setRecycleChildrenOnDetach(true);

        mRecyclerView.setRecycledViewPool(mActivity.getViewPool());
        mRecyclerView.addItemDecoration(new WidgetAdapter.WidgetItemDecoration(mActivity));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("title", mTitle);
    }

    @Override
    public boolean onItemClicked(Widget widget) {
        LinkedPage linkedPage = widget.linkedPage();
        if (mActivity != null && linkedPage != null) {
            mActivity.onWidgetSelected(linkedPage, WidgetListFragment.this);
            return true;
        }
        return false;
    }

    @Override
    public boolean onItemLongClicked(final Widget widget) {
        if (mSuggestedCommandsFactory == null) {
            mSuggestedCommandsFactory = new SuggestedCommandsFactory(getContext(), false);
        }
        SuggestedCommandsFactory.SuggestedCommands suggestedCommands =
                mSuggestedCommandsFactory.fill(widget);

        List<String> labels = suggestedCommands.labels;
        List<String> commands = suggestedCommands.commands;

        if (widget.linkedPage() != null) {
            labels.add(getString(R.string.nfc_action_to_sitemap_page));
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(getContext())) {
                labels.add(getString(R.string.home_shortcut_pin_to_home));
            }
        }

        if (!labels.isEmpty()) {
            final String[] labelArray = labels.toArray(new String[0]);
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.nfc_dialog_title)
                    .setItems(labelArray, (dialog, which) -> {
                        if (which < commands.size()) {
                            startActivity(WriteTagActivity.createItemUpdateIntent(
                                    getActivity(), widget.item().name(), commands.get(which),
                                    labels.get(which), widget.item().label()));
                        } else if (which == commands.size()) {
                            startActivity(WriteTagActivity.createSitemapNavigationIntent(
                                    getActivity(), widget.linkedPage().link()));
                        } else {
                            createShortcut(widget.linkedPage());
                        }
                    })
                    .show();

            return true;
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_widgetlist, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated() " + mPageUrl);
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = view.findViewById(R.id.recyclerview);
        mEmptyPageView = view.findViewById(android.R.id.empty);
        mRefreshLayout = view.findViewById(R.id.swiperefresh);

        Util.applySwipeLayoutColors(mRefreshLayout, R.attr.colorPrimary, R.attr.colorAccent);
        mRefreshLayout.setRecyclerView(mRecyclerView);
        mRefreshLayout.setOnRefreshListener(() -> {
            mActivity.showRefreshHintSnackbarIfNeeded();
            CacheManager.getInstance(getActivity()).clearCache();
            if (mPageUrl != null) {
                mActivity.triggerPageUpdate(mPageUrl, true);
            }
        });
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart() " + mPageUrl);
        super.onStart();
        mActivity.triggerPageUpdate(mPageUrl, false);
        if (mAdapter != null) {
            startOrStopVisibleViewHolders(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() " + mPageUrl);
        if (mAdapter != null) {
            startOrStopVisibleViewHolders(false);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void createShortcut(LinkedPage linkedPage) {
        String iconFormat = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString(Constants.PREFERENCE_ICON_FORMAT, "png");
        new AsyncTask<Void, Void, IconCompat>() {

            @Override
            protected IconCompat doInBackground(Void... voids) {
                Context context = getContext();
                String url = null;
                if (!TextUtils.isEmpty(linkedPage.iconPath())) {
                    url = new Uri.Builder()
                            .appendEncodedPath(linkedPage.iconPath())
                            .appendQueryParameter("format", iconFormat)
                            .toString();
                }
                IconCompat icon = null;
                Connection connection = null;
                try {
                    connection = ConnectionFactory.getUsableConnection();
                } catch (ConnectionException e) {
                    // ignored
                }

                if (context == null || connection == null) {
                    return null;
                }

                if (url != null) {
                    /**
                     *  Icon size is defined in {@link AdaptiveIconDrawable}. Foreground size of
                     *  46dp instead of 72dp adds enough border to the icon.
                     *  46dp foreground + 2 * 31dp border = 108dp
                     **/
                    int foregroundSize = (int) Util.convertDpToPixel(46, context);
                    Bitmap bitmap = connection.getSyncHttpClient().get(url)
                            .asBitmap(foregroundSize, true).response;
                    if (bitmap != null) {
                        bitmap = addBackgroundAndBorder(bitmap,
                                (int) Util.convertDpToPixel(31, context));
                        icon = IconCompat.createWithAdaptiveBitmap(bitmap);
                    }
                }

                if (icon == null) {
                    // Fall back to openHAB icon
                    icon = IconCompat.createWithResource(context, R.mipmap.icon);
                }

                return icon;
            }

            @Override
            protected void onPostExecute(IconCompat icon) {
                Context context = getContext();
                if (icon == null || context == null) {
                    return;
                }

                Uri sitemapUri = Uri.parse(linkedPage.link());
                String shortSitemapUri = sitemapUri.getPath().substring(14);

                Intent startIntent = new Intent(context, MainActivity.class);
                startIntent.setAction(MainActivity.ACTION_SITEMAP_SELECTED);
                startIntent.putExtra(MainActivity.EXTRA_SITEMAP_URL, shortSitemapUri);
                startActivity(startIntent);

                String name = linkedPage.title();
                if (TextUtils.isEmpty(name)) {
                    name = getString(R.string.app_name);
                }

                ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context,
                        shortSitemapUri + '-' + System.currentTimeMillis())
                        .setShortLabel(name)
                        .setIcon(icon)
                        .setIntent(startIntent)
                        .build();

                if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
                    Toasty.success(context,R.string.home_shortcut_success_pinning).show();
                } else {
                    Toasty.error(context,R.string.home_shortcut_error_pinning).show();
                }
            }
        }.execute();
    }

    /**
     * @author https://stackoverflow.com/a/15525394
     */
    private Bitmap addBackgroundAndBorder(Bitmap bitmap, int borderSize) {
        Bitmap bitmapWithBackground = Bitmap.createBitmap(bitmap.getWidth() + borderSize * 2,
                bitmap.getHeight() + borderSize * 2, bitmap.getConfig());
        Canvas canvas = new Canvas(bitmapWithBackground);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, borderSize, borderSize, null);
        return bitmapWithBackground;
    }

    public static WidgetListFragment withPage(String pageUrl, String pageTitle) {
        WidgetListFragment fragment = new WidgetListFragment();
        Bundle args = new Bundle();
        args.putString("displayPageUrl", pageUrl);
        args.putString("title", pageTitle);
        fragment.setArguments(args);
        return fragment;
    }

    public void setHighlightedPageLink(String highlightedPageLink) {
        mHighlightedPageLink = highlightedPageLink;
        if (mAdapter == null) {
            return;
        }
        if (highlightedPageLink != null) {
            for (int i = 0; i < mAdapter.getItemCount(); i++) {
                LinkedPage page = mAdapter.getItem(i).linkedPage();
                if (page != null && highlightedPageLink.equals(page.link())) {
                    if (mAdapter.setSelectedPosition(i)) {
                        mLayoutManager.scrollToPosition(i);
                    }
                    return;
                }
            }
        }
        // We didn't find a matching page link, so unselect everything
        mAdapter.setSelectedPosition(-1);
    }

    public void updateTitle(String pageTitle) {
        mTitle = pageTitle.replaceAll("[\\[\\]]", "");
        if (mActivity != null) {
            mActivity.updateTitle();
        }
    }

    public void updateWidgets(List<Widget> widgets) {
        if (mAdapter != null) {
            mAdapter.update(widgets, mRefreshLayout.isRefreshing());
            boolean emptyPage = widgets.size() == 0;
            mRecyclerView.setVisibility(emptyPage ? View.GONE : View.VISIBLE);
            mEmptyPageView.setVisibility(emptyPage ? View.VISIBLE : View.GONE);
            setHighlightedPageLink(mHighlightedPageLink);
            mRefreshLayout.setRefreshing(false);
        }
    }

    public void updateWidget(Widget widget) {
        if (mAdapter != null) {
            mAdapter.updateWidget(widget);
        }
    }

    public String getDisplayPageUrl() {
        return getArguments().getString("displayPageUrl");
    }

    public String getTitle() {
        if (mTitle != null) {
            return mTitle;
        }
        return getArguments().getString("title");
    }

    private void startOrStopVisibleViewHolders(boolean start) {
        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        final int lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition();
        for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; ++i) {
            WidgetAdapter.ViewHolder holder =
                    (WidgetAdapter.ViewHolder) mRecyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                if (start) {
                    holder.start();
                } else {
                    holder.stop();
                }
            }
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s [url=%s, title=%s]",
                super.toString(), mPageUrl, mTitle);
    }
}

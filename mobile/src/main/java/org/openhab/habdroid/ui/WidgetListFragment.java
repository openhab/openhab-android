/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.LabeledValue;
import org.openhab.habdroid.model.LinkedPage;
import org.openhab.habdroid.model.Widget;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
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
    private LinearLayoutManager mLayoutManager;
    private WidgetAdapter mAdapter;
    // Url of current sitemap page displayed
    private String mPageUrl;
    // parent activity
    private MainActivity mActivity;
    private boolean mIsVisible = false;
    private String mTitle;
    private SwipeRefreshLayout mRefreshLayout;
    private String mHighlightedPageLink;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Log.d(TAG, "isAdded = " + isAdded());
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
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
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
    public void onItemClicked(Widget widget) {
        LinkedPage linkedPage = widget.linkedPage();
        if (mActivity != null && linkedPage != null) {
            mActivity.onWidgetSelected(linkedPage, WidgetListFragment.this);
        }
    }

    @Override
    public void onItemLongClicked(final Widget widget) {
        Log.d(TAG, "Widget type = " + widget.type());

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> commands = new ArrayList<>();

        if (widget.item() != null) {
            // If the widget has mappings, we will populate names and commands with
            // values from those mappings
            if (widget.hasMappingsOrItemOptions()) {
                for (LabeledValue mapping : widget.getMappingsOrItemOptions()) {
                    labels.add(mapping.label());
                    commands.add(mapping.value());
                }
                // Else we only can do it for Switch widget with On/Off/Toggle commands
            } else if (widget.type() == Widget.Type.Switch) {
                Item item = widget.item();
                if (item.isOfTypeOrGroupType(Item.Type.Switch)) {
                    labels.add(getString(R.string.nfc_action_on));
                    commands.add("ON");
                    labels.add(getString(R.string.nfc_action_off));
                    commands.add("OFF");
                    labels.add(getString(R.string.nfc_action_toggle));
                    commands.add("TOGGLE");
                } else if (item.isOfTypeOrGroupType(Item.Type.Rollershutter)) {
                    labels.add(getString(R.string.nfc_action_up));
                    commands.add("UP");
                    labels.add(getString(R.string.nfc_action_down));
                    commands.add("DOWN");
                    labels.add(getString(R.string.nfc_action_toggle));
                    commands.add("TOGGLE");
                }
            } else if (widget.type() == Widget.Type.Colorpicker) {
                labels.add(getString(R.string.nfc_action_on));
                commands.add("ON");
                labels.add(getString(R.string.nfc_action_off));
                commands.add("OFF");
                labels.add(getString(R.string.nfc_action_toggle));
                commands.add("TOGGLE");
                labels.add(getString(R.string.nfc_action_current_color));
                commands.add(widget.item().state());
            }
        }
        labels.add(getString(R.string.nfc_action_to_sitemap_page));

        final String[] labelArray = labels.toArray(new String[0]);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.nfc_dialog_title)
                .setItems(labelArray, (dialog, which) -> {
                    Intent writeTagIntent = new Intent(getActivity(), WriteTagActivity.class);
                    writeTagIntent.putExtra("sitemapPage", mPageUrl);

                    if (which < labelArray.length - 1) {
                        writeTagIntent.putExtra("item", widget.item().name());
                        writeTagIntent.putExtra("itemType", widget.item().type());
                        writeTagIntent.putExtra("command", commands.get(which));
                    }
                    startActivityForResult(writeTagIntent, 0);
                    Util.overridePendingTransition(getActivity(), false);
                })
                .show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        Log.d(TAG, "isAdded = " + isAdded());
        return inflater.inflate(R.layout.fragment_widgetlist, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        Log.d(TAG, "isAdded = " + isAdded());
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = view.findViewById(R.id.recyclerview);
        mRefreshLayout = getView().findViewById(R.id.swiperefresh);

        Util.applySwipeLayoutColors(mRefreshLayout, R.attr.colorPrimary, R.attr.colorAccent);
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
        Log.d(TAG, "onStart");
        super.onStart();
        mActivity.triggerPageUpdate(mPageUrl, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() " + mPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (mAdapter != null) {
            stopVisibleViewHolders();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        mIsVisible = isVisibleToUser;
        Log.d(TAG, String.format("isVisibleToUser(%B)", isVisibleToUser));
    }

    public static WidgetListFragment withPage(String pageUrl, String pageTitle) {
        Log.d(TAG, "withPage(" + pageUrl + ")");
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

    public void update(String pageTitle, List<Widget> widgets) {
        mTitle = pageTitle;

        if (mAdapter != null) {
            mAdapter.update(widgets, mRefreshLayout.isRefreshing());
            setHighlightedPageLink(mHighlightedPageLink);
            mRefreshLayout.setRefreshing(false);
        }
        if (mActivity != null && mIsVisible) {
            mActivity.updateTitle();
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

    private void stopVisibleViewHolders() {
        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        final int lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition();
        for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; ++i) {
            WidgetAdapter.ViewHolder holder =
                    (WidgetAdapter.ViewHolder) mRecyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                holder.stop();
            }
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s [url=%s, title=%s]",
                super.toString(), mPageUrl, mTitle);
    }
}

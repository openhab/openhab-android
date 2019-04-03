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
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.Item;
import org.openhab.habdroid.model.LabeledValue;
import org.openhab.habdroid.model.LinkedPage;
import org.openhab.habdroid.model.Widget;
import org.openhab.habdroid.ui.widget.RecyclerViewSwipeRefreshLayout;
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
    public void onItemLongClicked(final Widget widget) {
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
                if (widget.state() != null) {
                    labels.add(getString(R.string.nfc_action_current_color));
                    commands.add(widget.state().asString());
                }
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

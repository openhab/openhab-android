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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABLabeledValue;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

public class OpenHABWidgetListFragment extends Fragment
        implements OpenHABWidgetAdapter.ItemClickListener {
    private static final String TAG = OpenHABWidgetListFragment.class.getSimpleName();
    // List adapter for list view of openHAB widgets
    private OpenHABWidgetAdapter openHABWidgetAdapter;
    @VisibleForTesting
    public RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    // Url of current sitemap page displayed
    private String displayPageUrl;
    // parent activity
    private OpenHABMainActivity mActivity;
    // Am I visible?
    private boolean mIsVisible = false;
    private String mTitle;
    private SwipeRefreshLayout refreshLayout;
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
        displayPageUrl = args.getString("displayPageUrl");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
        mActivity = (OpenHABMainActivity) getActivity();

        // We're using atmosphere so create an own client to not block the others
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mActivity);

        openHABWidgetAdapter = new OpenHABWidgetAdapter(getActivity(),
                mActivity.getConnection(), this);

        mLayoutManager = new LinearLayoutManager(mActivity);
        mLayoutManager.setRecycleChildrenOnDetach(true);

        mRecyclerView.setRecycledViewPool(mActivity.getViewPool());
        mRecyclerView.addItemDecoration(new OpenHABWidgetAdapter.WidgetItemDecoration(mActivity));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(openHABWidgetAdapter);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("title", mTitle);
    }

    @Override
    public void onItemClicked(OpenHABWidget openHABWidget) {
        OpenHABLinkedPage linkedPage = openHABWidget.linkedPage();
        if (mActivity != null && linkedPage != null) {
            mActivity.onWidgetSelected(linkedPage, OpenHABWidgetListFragment.this);
        }
    }

    @Override
    public void onItemLongClicked(final OpenHABWidget widget) {
        Log.d(TAG, "Widget type = " + widget.type());

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> commands = new ArrayList<>();

        if (widget.item() != null) {
            // If the widget has mappings, we will populate names and commands with
            // values from those mappings
            if (widget.hasMappingsOrItemOptions()) {
                for (OpenHABLabeledValue mapping : widget.getMappingsOrItemOptions()) {
                    labels.add(mapping.label());
                    commands.add(mapping.value());
                }
                // Else we only can do it for Switch widget with On/Off/Toggle commands
            } else if (widget.type() == OpenHABWidget.Type.Switch) {
                OpenHABItem item = widget.item();
                if (item.isOfTypeOrGroupType(OpenHABItem.Type.Switch)) {
                    labels.add(getString(R.string.nfc_action_on));
                    commands.add("ON");
                    labels.add(getString(R.string.nfc_action_off));
                    commands.add("OFF");
                    labels.add(getString(R.string.nfc_action_toggle));
                    commands.add("TOGGLE");
                } else if (item.isOfTypeOrGroupType(OpenHABItem.Type.Rollershutter)) {
                    labels.add(getString(R.string.nfc_action_up));
                    commands.add("UP");
                    labels.add(getString(R.string.nfc_action_down));
                    commands.add("DOWN");
                    labels.add(getString(R.string.nfc_action_toggle));
                    commands.add("TOGGLE");
                }
            } else if (widget.type() == OpenHABWidget.Type.Colorpicker) {
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

        final String[] labelArray = labels.toArray(new String[labels.size()]);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.nfc_dialog_title)
                .setItems(labelArray, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent writeTagIntent = new Intent(getActivity(), OpenHABWriteTagActivity.class);
                        writeTagIntent.putExtra("sitemapPage", displayPageUrl);

                        if (which < labelArray.length - 1) {
                            writeTagIntent.putExtra("item", widget.item().name());
                            writeTagIntent.putExtra("itemType", widget.item().type());
                            writeTagIntent.putExtra("command", commands.get(which));
                        }
                        startActivityForResult(writeTagIntent, 0);
                        Util.overridePendingTransition(getActivity(), false);
                    }
                })
                .show();
    }

    @NonNull
    private String getIconFormat() {
        return PreferenceManager.getDefaultSharedPreferences(mActivity).getString("iconFormatType","PNG");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "onCreateView");
        Log.d(TAG, "isAdded = " + isAdded());
        return inflater.inflate(R.layout.openhabwidgetlist_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        Log.d(TAG, "isAdded = " + isAdded());
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = view.findViewById(R.id.recyclerview);
        refreshLayout = getView().findViewById(R.id.swiperefresh);

        Util.applySwipeLayoutColors(refreshLayout, R.attr.colorPrimary, R.attr.colorAccent);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mActivity.showRefreshHintSnackbarIfNeeded();
                CacheManager.getInstance(getActivity()).clearCache();
                if (displayPageUrl != null) {
                    mActivity.triggerPageUpdate(displayPageUrl, true);
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        mActivity.triggerPageUpdate(displayPageUrl, false);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onPause () {
        super.onPause();
        Log.d(TAG, "onPause() " + displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (openHABWidgetAdapter != null) {
            stopVisibleViewHolders();
        }
    }

    @Override
    public void setUserVisibleHint (boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        mIsVisible = isVisibleToUser;
        Log.d(TAG, String.format("isVisibleToUser(%B)", isVisibleToUser));
    }

    public static OpenHABWidgetListFragment withPage(String pageUrl, String pageTitle) {
        Log.d(TAG, "withPage(" + pageUrl + ")");
        OpenHABWidgetListFragment fragment = new OpenHABWidgetListFragment();
        Bundle args = new Bundle();
        args.putString("displayPageUrl", pageUrl);
        args.putString("title", pageTitle);
        fragment.setArguments(args);
        return fragment;
    }

    public void setHighlightedPageLink(String highlightedPageLink) {
        mHighlightedPageLink = highlightedPageLink;
        if (openHABWidgetAdapter == null) {
            return;
        }
        openHABWidgetAdapter.setSelectedPosition(-1);
        if (highlightedPageLink != null) {
            for (int i = 0; i < openHABWidgetAdapter.getItemCount(); i++) {
                OpenHABLinkedPage page = openHABWidgetAdapter.getItem(i).linkedPage();
                if (page != null && highlightedPageLink.equals(page.link())) {
                    openHABWidgetAdapter.setSelectedPosition(i);
                    mLayoutManager.scrollToPosition(i);
                    break;
                }
            }
        }
    }

    public void update(String pageTitle, List<OpenHABWidget> widgets) {
        mTitle = pageTitle;

        if (openHABWidgetAdapter != null) {
            openHABWidgetAdapter.update(widgets);
            setHighlightedPageLink(mHighlightedPageLink);
            refreshLayout.setRefreshing(false);
        }
        if (mActivity != null && mIsVisible) {
            mActivity.updateTitle();
        }
    }

    public void updateWidget(OpenHABWidget widget) {
        if (openHABWidgetAdapter != null) {
            openHABWidgetAdapter.updateWidget(widget);
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
            OpenHABWidgetAdapter.ViewHolder holder =
                    (OpenHABWidgetAdapter.ViewHolder) mRecyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                holder.stop();
            }
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s [url=%s, title=%s]",
                super.toString(), displayPageUrl, mTitle);
    }
}

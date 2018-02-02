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
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABNFCActionList;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.util.Util;

import java.util.List;

import static org.openhab.habdroid.core.message.MessageHandler.LOGLEVEL_ALWAYS;
import static org.openhab.habdroid.core.message.MessageHandler.TYPE_SNACKBAR;
import static org.openhab.habdroid.util.Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED;

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

public class OpenHABWidgetListFragment extends Fragment
        implements OpenHABWidgetAdapter.ItemClickListener {
    private static final String TAG = OpenHABWidgetListFragment.class.getSimpleName();
    // List adapter for list view of openHAB widgets
    private OpenHABWidgetAdapter openHABWidgetAdapter;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    // Url of current sitemap page displayed
    private String displayPageUrl;
    // selected openhab widget
    private OpenHABWidget selectedOpenHABWidget;
    // parent activity
    private OpenHABMainActivity mActivity;
    // Am I visible?
    private boolean mIsVisible = false;
    private String mTitle;
    private List<OpenHABWidget> mWidgets;
    private SwipeRefreshLayout refreshLayout;

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
        final String iconFormat = getIconFormat();

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

        if (mWidgets != null) {
            update(mTitle, mWidgets);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("title", mTitle);
    }

    @Override
    public boolean onItemClicked(OpenHABWidget openHABWidget) {
        if (!openHABWidget.hasLinkedPage()) {
            return false;
        }

        // Widget have a page linked to it
        if (mActivity != null) {
            mActivity.onWidgetSelected(openHABWidget.getLinkedPage(), OpenHABWidgetListFragment.this);
        }
        return true;
    }

    @Override
    public void onItemLongClicked(OpenHABWidget openHABWidget) {
        Log.d(TAG, "Widget type = " + openHABWidget.getType());

        selectedOpenHABWidget = openHABWidget;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.nfc_dialog_title);
        final OpenHABNFCActionList nfcActionList = new OpenHABNFCActionList
                (selectedOpenHABWidget, getContext());
        builder.setItems(nfcActionList.getNames(), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent writeTagIntent = new Intent(getActivity().getApplicationContext(),
                        OpenHABWriteTagActivity.class);
                writeTagIntent.putExtra("sitemapPage", displayPageUrl);

                if (nfcActionList.getCommands().length > which) {
                    writeTagIntent.putExtra("item", selectedOpenHABWidget.getItem().getName());
                    writeTagIntent.putExtra("itemType", selectedOpenHABWidget.getItem().getType());
                    writeTagIntent.putExtra("command", nfcActionList.getCommands()[which]);
                }
                startActivityForResult(writeTagIntent, 0);
                Util.overridePendingTransition(getActivity(), false);
                selectedOpenHABWidget = null;
            }
        });
        builder.show();
    }

    private void showSwipeToRefreshDescriptionSnackbar() {
        mActivity.getMessageHandler().showMessageToUser(
                getString(R.string.swipe_to_refresh_description),
                TYPE_SNACKBAR, LOGLEVEL_ALWAYS,
                R.string.swipe_to_refresh_dismiss, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PreferenceManager
                                .getDefaultSharedPreferences(v.getContext())
                                .edit()
                                .putBoolean(PREFERENCE_SWIPE_REFRESH_EXPLAINED, true)
                                .apply();
                    }
                });
    }

    private boolean shouldShowSwipeToRefreshDescriptionSnackbar() {
        return !PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean
                (PREFERENCE_SWIPE_REFRESH_EXPLAINED, false);
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

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getActivity().getTheme();

        theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
        @ColorInt int colorPrimary = typedValue.data;

        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        @ColorInt int colorAccent = typedValue.data;

        refreshLayout.setColorSchemeColors(colorPrimary, colorAccent);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (shouldShowSwipeToRefreshDescriptionSnackbar()) {
                    showSwipeToRefreshDescriptionSnackbar();
                }
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
        getArguments().putString("highlightedPageLink", highlightedPageLink);
        if (openHABWidgetAdapter == null) {
            return;
        }
        openHABWidgetAdapter.setSelectedPosition(-1);
        if (highlightedPageLink != null) {
            for (int i = 0; i < openHABWidgetAdapter.getItemCount(); i++) {
                OpenHABLinkedPage page = openHABWidgetAdapter.getItem(i).getLinkedPage();
                if (page != null && highlightedPageLink.equals(page.getLink())) {
                    openHABWidgetAdapter.setSelectedPosition(i);
                    mLayoutManager.scrollToPosition(i);
                    break;
                }
            }
        }
    }

    public void update(String pageTitle, List<OpenHABWidget> widgets) {
        mTitle = pageTitle;
        mWidgets = widgets;

        if (openHABWidgetAdapter != null) {
            openHABWidgetAdapter.update(widgets);
            setHighlightedPageLink(getArguments().getString("highlightedPageLink"));
            refreshLayout.setRefreshing(false);
        }
        if (mActivity != null && mIsVisible) {
            mActivity.updateTitle();
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

    public void clearSelection() {
        Log.d(TAG, "clearSelection() " + this.displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (openHABWidgetAdapter != null) {
            openHABWidgetAdapter.setSelectedPosition(-1);
        }
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
}

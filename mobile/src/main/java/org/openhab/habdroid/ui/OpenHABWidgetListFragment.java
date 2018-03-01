/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.Activity;
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
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABNFCActionList;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Headers;

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
    // Datasource, providing list of openHAB widgets
    private OpenHABWidgetDataSource openHABWidgetDataSource;
    // List adapter for list view of openHAB widgets
    private OpenHABWidgetAdapter openHABWidgetAdapter;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    // Url of current sitemap page displayed
    private String displayPageUrl;
    // selected openhab widget
    private OpenHABWidget selectedOpenHABWidget;
    // widget Id which we got from nfc tag
    private String nfcWidgetId;
    // widget command which we got from nfc tag
    private String nfcCommand;
    // auto close app after nfc action is complete
    private boolean nfcAutoClose = false;
    // parent activity
    private OpenHABMainActivity mActivity;
    // Am I visible?
    private boolean mIsVisible = false;
    private int mPosition;
    private String mTitle;
    private String mAtmosphereTrackingId;
    // keeps track of current request to cancel it in onPause
    private Call mRequestHandle;
    private SwipeRefreshLayout refreshLayout;
    private Connection mConnection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Log.d(TAG, "isAdded = " + isAdded());
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            displayPageUrl = getArguments().getString("displayPageUrl");
            mPosition = getArguments().getInt("position");
            mTitle = getArguments().getString("title");
        }
        if (savedInstanceState != null) {
            mTitle = savedInstanceState.getString("title");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
        mActivity = (OpenHABMainActivity) getActivity();
        mConnection = mActivity.getConnection();
        final String iconFormat = getIconFormat();
        openHABWidgetDataSource = new OpenHABWidgetDataSource(iconFormat);

        // We're using atmosphere so create an own client to not block the others
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mActivity);

        openHABWidgetAdapter = new OpenHABWidgetAdapter(getActivity(), this,
                getResources().getInteger(R.integer.pager_columns) > 1, mConnection);

        if (savedInstanceState != null) {
            openHABWidgetAdapter.setSelectedPosition(savedInstanceState.getInt("selection", -1));
        }
        mLayoutManager = new LinearLayoutManager(mActivity);
        mLayoutManager.setRecycleChildrenOnDetach(true);

        mRecyclerView.setRecycledViewPool(mActivity.getViewPool());
        mRecyclerView.addItemDecoration(new OpenHABWidgetAdapter.WidgetItemDecoration(mActivity));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(openHABWidgetAdapter);


        refreshLayout = getView().findViewById(R.id.swiperefresh);
        if (refreshLayout == null) {
            return;
        }

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
                    showPage(displayPageUrl, false);
                }
            }
        });

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("title", mTitle);
        outState.putInt("selection", openHABWidgetAdapter.getSelectedPosition());
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
    }

    @Override
    public void onPause () {
        super.onPause();
        Log.d(TAG, "onPause() " + displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        // We only have 1 request running per fragment so
        // cancel it if we have it
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run(){
                if (mRequestHandle != null) {
                    mRequestHandle.cancel();
                    mRequestHandle = null;
                }
            }
        });
        thread.start();
        if (openHABWidgetAdapter != null) {
            stopVisibleViewHolders();
        }
    }

    @Override
    public void onResume () {
        super.onResume();
        Log.d(TAG, "onResume() " + displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (displayPageUrl != null && mConnection != null)
            showPage(displayPageUrl, false);
    }

    @Override
    public void setUserVisibleHint (boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        mIsVisible = isVisibleToUser;
        Log.d(TAG, String.format("isVisibleToUser(%B)", isVisibleToUser));
    }

    public static OpenHABWidgetListFragment withPage(String pageUrl, String pageTitle,
                                                     int position) {
        Log.d(TAG, "withPage(" + pageUrl + ")");
        OpenHABWidgetListFragment fragment = new OpenHABWidgetListFragment();
        Bundle args = new Bundle();
        args.putString("displayPageUrl", pageUrl);
        args.putString("title", pageTitle);
        args.putInt("position", position);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Loads data from sitemap page URL and passes it to processContent
     *
     * @param  pageUrl  an absolute base URL of openHAB sitemap page
     * @param  longPolling  enable long polling when loading page
     * @return      void
     */
    public void showPage(String pageUrl, final boolean longPolling) {
        Log.i(TAG, " showPage for " + pageUrl + " longPolling = " + longPolling);
        Log.d(TAG, "isAdded = " + isAdded());
        // Cancel any existing http request to openHAB (typically ongoing long poll)
        if (mRequestHandle != null) {
            mRequestHandle.cancel();
            mRequestHandle = null;
        }
        if (!longPolling) {
            startProgressIndicator();
            this.mAtmosphereTrackingId = null;
        }
        Map<String, String> headers = new HashMap<String, String>();
        if (mActivity.getOpenHABVersion() == 1) {
            headers.put("Accept", "application/xml");
        }

        MyAsyncHttpClient asyncHttpClient = mConnection.getAsyncHttpClient();
        headers.put("X-Atmosphere-Framework", "1.0");
        if (longPolling) {
            asyncHttpClient.setTimeout(300000);
            headers.put("X-Atmosphere-Transport", "long-polling");
            if (this.mAtmosphereTrackingId == null) {
                headers.put("X-Atmosphere-tracking-id", "0");
            } else {
                headers.put("X-Atmosphere-tracking-id", this.mAtmosphereTrackingId);
            }
        } else {
            headers.put("X-Atmosphere-tracking-id", "0");
            asyncHttpClient.setTimeout(10000);
        }
        mRequestHandle = asyncHttpClient.get(pageUrl, headers, new MyHttpClient.ResponseHandler() {
                    @Override
                    public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                        if (call.isCanceled()) {
                            Log.i(TAG, "Call canceled on failure - stop updating");
                            return;
                        }
                        mAtmosphereTrackingId = null;
                        if (!longPolling)
                            stopProgressIndicator();
                        if (error instanceof SocketTimeoutException) {
                            Log.d(TAG, "Connection timeout, reconnecting");
                            showPage(displayPageUrl, false);
                            return;
                        } else {
                    /*
                    * If we get a network error try connecting again, if the
                    * fragment is paused, the runnable will be removed
                    */
                            Log.e(TAG, error.toString());
                            Log.e(TAG, String.format("status code = %d", statusCode));
                            Log.e(TAG, "Connection error = " + error.getClass().toString() + ", cycle aborted");

//                            networkHandler.removeCallbacks(networkRunnable);
//                            networkRunnable =  new Runnable(){
//                                @Override
//                                public void run(){
                                    showPage(displayPageUrl, false);
//                                }
//                            };
//                            networkHandler.postDelayed(networkRunnable, 10 * 1000);
                        }
                    }

                    @Override
                    public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                        if (call.isCanceled()) {
                            Log.i(TAG, "Call canceled on success - stop updating");
                            return;
                        }
                        String id = headers.get("X-Atmosphere-tracking-id");
                        if (id != null) {
                            Log.i(TAG, "Found atmosphere tracking id: " + id);
                            OpenHABWidgetListFragment.this.mAtmosphereTrackingId = id;
                        }
                        if (!longPolling)
                            stopProgressIndicator();
                        String responseString = new String(responseBody);
                        processContent(responseString, longPolling);
                    }
                });
    }

    /**
     * Parse XML sitemap page and show it
     *
     *
     * @return      void
     */
    public void processContent(String responseString, boolean longPolling) {

        Log.d(TAG, "processContent() " + this.displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        Log.d(TAG, "responseString.length() = " + (responseString != null ? responseString.length()  : -1));

        // We can receive empty response, probably when no items was changed
        // so we needn't process it
        if (responseString == null || responseString.length() == 0) {
            showPage(displayPageUrl, true);
            return;
        }

        List<OpenHABWidget> widgetList = new ArrayList<>();

        // If openHAB verion = 1 get page from XML
        if (mActivity.getOpenHABVersion() == 1) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder builder = dbf.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(responseString)));
                if (document != null) {
                    Node rootNode = document.getFirstChild();
                    openHABWidgetDataSource.setSourceNode(rootNode);
                    widgetList.clear();
                    for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
                        // Remove frame widgets with no label text
                        if (w.getType().equals("Frame") && TextUtils.isEmpty(w.getLabel()))
                            continue;
                        widgetList.add(w);
                    }
                } else {
                    Log.e(TAG, "Got a null response from openHAB");
                    showPage(displayPageUrl, false);
                }
            } catch (ParserConfigurationException | SAXException | IOException e) {
                    Log.d(TAG, "responseString:\n" + String.valueOf(responseString));
                    Log.e(TAG, e.getMessage(), e);
            }
            // Later versions work with JSON
        } else {
            try {
                JSONObject pageJson = new JSONObject(responseString);
                // In case of a server timeout in the long polling request, nothing is done
                // and the request is restarted
                if (longPolling && pageJson.has("timeout")
                        && pageJson.getString("timeout").equalsIgnoreCase("true")) {
                    Log.e(TAG, "Server timeout in the long polling request");
                    showPage(displayPageUrl, true);
                    return;
                }
                openHABWidgetDataSource.setSourceJson(pageJson);
                widgetList.clear();
                for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    if (w.getType().equals("Frame") && TextUtils.isEmpty(w.getLabel()))
                        continue;
                    widgetList.add(w);
                }
            } catch (JSONException e) {
                Log.d(TAG, e.getMessage(), e);
            }
        }

        openHABWidgetAdapter.update(widgetList);
        mTitle = openHABWidgetDataSource.getTitle();
        if (mActivity != null && mIsVisible) {
            mActivity.updateTitle();
        }
        // Set widget list index to saved or zero position
        // This would mean we got widget and command from nfc tag, so we need to do some automatic actions!
        if (this.nfcWidgetId != null && this.nfcCommand != null) {
            Log.d(TAG, "Have widget and command, NFC action!");
            OpenHABWidget nfcWidget = this.openHABWidgetDataSource.getWidgetById(this.nfcWidgetId);
            OpenHABItem nfcItem = nfcWidget.getItem();
            // Found widget with id from nfc tag and it has an item
            if (nfcWidget != null && nfcItem != null) {
                // TODO: Perform nfc widget action here
                MyAsyncHttpClient client = mConnection.getAsyncHttpClient();
                if (this.nfcCommand.equals("TOGGLE")) {
                    //RollerShutterItem changed to RollerShutter in later builds of OH2
                    if (nfcItem.getType().startsWith("Rollershutter")) {
                        if (nfcItem.getStateAsBoolean())
                            Util.sendItemCommand(client, nfcItem, "UP");
                        else
                            Util.sendItemCommand(client, nfcItem, "DOWN");
                    } else {
                        if (nfcItem.getStateAsBoolean())
                            Util.sendItemCommand(client, nfcItem, "OFF");
                        else
                            Util.sendItemCommand(client, nfcItem, "ON");
                    }
                } else {
                    Util.sendItemCommand(client, nfcItem, this.nfcCommand);
                }
            }
            this.nfcWidgetId = null;
            this.nfcCommand = null;
            if (this.nfcAutoClose) {
                getActivity().finish();
            }
        }

        showPage(displayPageUrl, true);
    }

    private void stopProgressIndicator() {
        if (mActivity != null) {
            Log.d(TAG, "Stop progress indicator");
            mActivity.setProgressIndicatorVisible(false);
        }
        if (refreshLayout != null)
            refreshLayout.setRefreshing(false);
    }

    private void startProgressIndicator() {
        boolean swipeAlreadyLoading = refreshLayout != null && refreshLayout.isRefreshing();

        if (mActivity != null && !swipeAlreadyLoading) {
            Log.d(TAG, "Start progress indicator");
            mActivity.setProgressIndicatorVisible(true);
        }
    }

    public String getDisplayPageUrl() {
        return displayPageUrl;
    }

    public String getTitle() {
        return mTitle;
    }

    public void clearSelection() {
        Log.d(TAG, "clearSelection() " + this.displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (openHABWidgetAdapter != null) {
            openHABWidgetAdapter.setSelectedPosition(-1);
        }
    }

    public int getPosition() {
        return mPosition;
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

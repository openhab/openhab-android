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
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABItem;
import org.openhab.habdroid.model.OpenHABNFCActionList;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.openhab.habdroid.util.Constants;
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
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Headers;

/**
 * This class is apps' main fragment which displays list of openHAB
 * widgets from sitemap page with further navigation through sitemap and everything else!
 */

public class OpenHABWidgetListFragment extends ListFragment {
    private static final String TAG = OpenHABWidgetListFragment.class.getSimpleName();
    private OnWidgetSelectedListener widgetSelectedListener;
    // Datasource, providing list of openHAB widgets
    private OpenHABWidgetDataSource openHABWidgetDataSource;
    // List adapter for list view of openHAB widgets
    private OpenHABWidgetAdapter openHABWidgetAdapter;
    // Url of current sitemap page displayed
    // Url of current sitemap page displayed
    private String displayPageUrl;
    // sitemap root url
    private String sitemapRootUrl = "";
    // openHAB base url
    private String openHABBaseUrl = "http://demo.openhab.org:8080/";
    // List of widgets to display
    private ArrayList<OpenHABWidget> widgetList = new ArrayList<OpenHABWidget>();
    // Username/password for authentication
    private String openHABUsername = "";
    private String openHABPassword = "";
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
    // loopj
    private MyAsyncHttpClient mAsyncHttpClient;
    // Am I visible?
    private boolean mIsVisible = false;
    private OpenHABWidgetListFragment mTag;
    private int mCurrentSelectedItem = -1;
    private int mPosition;
    private int mOldSelectedItem = -1;
    private String mAtmosphereTrackingId;
    //handlers will reconnect the network during outages
    private Handler networkHandler = new Handler();
    private Runnable networkRunnable;
    // keeps track of current request to cancel it in onPause
    private Call mRequestHandle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Log.d(TAG, "isAdded = " + isAdded());
        mTag = this;
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            displayPageUrl = getArguments().getString("displayPageUrl");
            openHABBaseUrl = getArguments().getString("openHABBaseUrl");
            sitemapRootUrl = getArguments().getString("sitemapRootUrl");
            openHABUsername = getArguments().getString("openHABUsername");
            openHABPassword = getArguments().getString("openHABPassword");
            mPosition = getArguments().getInt("position");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated()");
        Log.d(TAG, "isAdded = " + isAdded());
        mActivity = (OpenHABMainActivity)getActivity();
        final String iconFormat = PreferenceManager.getDefaultSharedPreferences(mActivity).getString("iconFormatType","PNG");
        openHABWidgetDataSource = new OpenHABWidgetDataSource(iconFormat);
        openHABWidgetAdapter = new OpenHABWidgetAdapter(getActivity(),
                R.layout.openhabwidgetlist_genericitem, widgetList);
        getListView().setAdapter(openHABWidgetAdapter);
        openHABBaseUrl = mActivity.getOpenHABBaseUrl();
        openHABUsername = mActivity.getOpenHABUsername();
        openHABPassword = mActivity.getOpenHABPassword();
        // We're using atmosphere so create an own client to not block the others
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mActivity);
        mAsyncHttpClient = new MyAsyncHttpClient(mActivity, prefs.getBoolean(Constants.PREFERENCE_SSLHOST,
                        false), prefs.getBoolean(Constants.PREFERENCE_SSLCERT, false));
        mAsyncHttpClient.setBasicAuth(openHABUsername, openHABPassword);
        openHABWidgetAdapter.setOpenHABUsername(openHABUsername);
        openHABWidgetAdapter.setOpenHABPassword(openHABPassword);
        openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
        openHABWidgetAdapter.setAsyncHttpClient(mAsyncHttpClient);
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                Log.d(TAG, "Widget clicked " + String.valueOf(position));
                OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
                if (openHABWidget.hasLinkedPage()) {
                    // Widget have a page linked to it
                    String[] splitString;
                    splitString = openHABWidget.getLinkedPage().getTitle().split("\\[|\\]");
                    if (OpenHABWidgetListFragment.this.widgetSelectedListener != null) {
                        widgetSelectedListener.onWidgetSelectedListener(openHABWidget.getLinkedPage(),
                                OpenHABWidgetListFragment.this);
                    }
//                        navigateToPage(openHABWidget.getLinkedPage().getLink(), splitString[0]);
                    mOldSelectedItem = position;
                } else {
                    Log.d(TAG, String.format("Click on item with no linked page, reverting selection to item %d", mOldSelectedItem));
                    // If an item without a linked page is clicked this will clear the selection
                    // and revert it to previously selected item (if any) when CHOICE_MODE_SINGLE
                    // is switched on for widget listview in multi-column mode on tablets
                    getListView().clearChoices();
                    getListView().requestLayout();
                    getListView().setItemChecked(mOldSelectedItem, true);
                }
            }

        });
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                Log.d(TAG, "Widget long-clicked " + String.valueOf(position));
                OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
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
                return true;
            }
        });
        if (getResources().getInteger(R.integer.pager_columns) > 1) {
            Log.d(TAG, "More then 1 column, setting selector on");
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "onAttach()");
        Log.d(TAG, "isAdded = " + isAdded());
        if (activity instanceof OnWidgetSelectedListener) {
            widgetSelectedListener = (OnWidgetSelectedListener)activity;
            mActivity = (OpenHABMainActivity)activity;
        } else {
            Log.e("TAG", "Attached to incompatible activity");
        }
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
            openHABWidgetAdapter.stopImageRefresh();
            openHABWidgetAdapter.stopVideoWidgets();
        }
        if (isAdded())
            mCurrentSelectedItem = getListView().getCheckedItemPosition();
    }

    @Override
    public void onResume () {
        super.onResume();
        Log.d(TAG, "onResume() " + displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (displayPageUrl != null)
            showPage(displayPageUrl, false);
    }

    @Override
    public void setUserVisibleHint (boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        mIsVisible = isVisibleToUser;
        Log.d(TAG, String.format("isVisibleToUser(%B)", isVisibleToUser));
    }

    public static OpenHABWidgetListFragment withPage(String pageUrl, String baseUrl, String rootUrl,
                                                     String username, String password, int position) {
        Log.d(TAG, "withPage(" + pageUrl + ")");
        OpenHABWidgetListFragment fragment = new OpenHABWidgetListFragment();
        Bundle args = new Bundle();
        args.putString("displayPageUrl", pageUrl);
        args.putString("openHABBaseUrl", baseUrl);
        args.putString("sitemapRootUrl", rootUrl);
        args.putString("openHABUsername", username);
        args.putString("openHABPassword", password);
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
        headers.put("X-Atmosphere-Framework", "1.0");
        if (longPolling) {
            mAsyncHttpClient.setTimeout(300000);
            headers.put("X-Atmosphere-Transport", "long-polling");
            if (this.mAtmosphereTrackingId == null) {
                headers.put("X-Atmosphere-tracking-id", "0");
            } else {
                headers.put("X-Atmosphere-tracking-id", this.mAtmosphereTrackingId);
            }
        } else {
            headers.put("X-Atmosphere-tracking-id", "0");
            mAsyncHttpClient.setTimeout(10000);
        }
        mRequestHandle = mAsyncHttpClient.get(pageUrl, headers, new MyHttpClient.ResponseHandler() {
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
                        // Log.d(TAG, responseString);
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

        // If openHAB verion = 1 get page from XML
        if (mActivity.getOpenHABVersion() == 1) {
            // As we change the page we need to stop all videos on current page
            // before going to the new page. This is quite dirty, but is the only
            // way to do that...
            openHABWidgetAdapter.stopVideoWidgets();
            openHABWidgetAdapter.stopImageRefresh();
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
                // As we change the page we need to stop all videos on current page
                // before going to the new page. This is quite dirty, but is the only
                // way to do that...
                openHABWidgetAdapter.stopVideoWidgets();
                openHABWidgetAdapter.stopImageRefresh();
                openHABWidgetDataSource.setSourceJson(pageJson);
                widgetList.clear();
                for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
                    // Remove frame widgets with no label text
                    if (w.getType().equals("Frame") && TextUtils.isEmpty(w.getLabel()))
                        continue;
                    widgetList.add(w);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        openHABWidgetAdapter.notifyDataSetChanged();
        if (!longPolling && isAdded()) {
            getListView().clearChoices();
            Log.d(TAG, String.format("processContent selectedItem = %d", mCurrentSelectedItem));
            if (mCurrentSelectedItem >= 0)
                getListView().setItemChecked(mCurrentSelectedItem, true);
        }
        if (getActivity() != null && mIsVisible)
            getActivity().setTitle(openHABWidgetDataSource.getTitle());
//            }
        // Set widget list index to saved or zero position
        // This would mean we got widget and command from nfc tag, so we need to do some automatic actions!
        if (this.nfcWidgetId != null && this.nfcCommand != null) {
            Log.d(TAG, "Have widget and command, NFC action!");
            OpenHABWidget nfcWidget = this.openHABWidgetDataSource.getWidgetById(this.nfcWidgetId);
            OpenHABItem nfcItem = nfcWidget.getItem();
            // Found widget with id from nfc tag and it has an item
            if (nfcWidget != null && nfcItem != null) {
                // TODO: Perform nfc widget action here
                if (this.nfcCommand.equals("TOGGLE")) {
                    //RollerShutterItem changed to RollerShutter in later builds of OH2
                    if (nfcItem.getType().startsWith("Rollershutter")) {
                        if (nfcItem.getStateAsBoolean())
                            this.openHABWidgetAdapter.sendItemCommand(nfcItem, "UP");
                        else
                            this.openHABWidgetAdapter.sendItemCommand(nfcItem, "DOWN");
                    } else {
                        if (nfcItem.getStateAsBoolean())
                            this.openHABWidgetAdapter.sendItemCommand(nfcItem, "OFF");
                        else
                            this.openHABWidgetAdapter.sendItemCommand(nfcItem, "ON");
                    }
                } else {
                    this.openHABWidgetAdapter.sendItemCommand(nfcItem, this.nfcCommand);
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
    }

    private void startProgressIndicator() {
        if (mActivity != null) {
            Log.d(TAG, "Start progress indicator");
            mActivity.setProgressIndicatorVisible(true);
        }
    }

    private void showAlertDialog(String alertMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(alertMessage)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void setOpenHABUsername(String openHABUsername) {
        this.openHABUsername = openHABUsername;
    }

    public void setOpenHABPassword(String openHABPassword) {
        this.openHABPassword = openHABPassword;
    }

    public void setDisplayPageUrl(String displayPageUrl) {
        this.displayPageUrl = displayPageUrl;
    }

    public String getDisplayPageUrl() {
        return displayPageUrl;
    }

    public String getTitle() {
        Log.d(TAG, "getPageTitle()");
        if (openHABWidgetDataSource != null)
            return openHABWidgetDataSource.getTitle();
        return "";
    }

    public void clearSelection() {
        Log.d(TAG, "clearSelection() " + this.displayPageUrl);
        Log.d(TAG, "isAdded = " + isAdded());
        if (getListView() != null && this.isVisible() && isAdded()) {
            getListView().clearChoices();
            getListView().requestLayout();
        }
    }

    public int getPosition() {
        return mPosition;
    }

}

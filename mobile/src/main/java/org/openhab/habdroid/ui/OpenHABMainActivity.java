/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.crittercism.app.Crittercism;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;
import com.loopj.android.image.WebImageCache;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.HttpResponseException;
import cz.msebera.android.httpclient.conn.HttpHostConnectException;
import cz.msebera.android.httpclient.entity.StringEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.HABDroid;
import org.openhab.habdroid.core.NetworkConnectivityInfo;
import org.openhab.habdroid.core.NotificationDeletedBroadcastReceiver;
import org.openhab.habdroid.core.OpenHABTracker;
import org.openhab.habdroid.core.OpenHABTrackerReceiver;
import org.openhab.habdroid.core.OpenHABVoiceService;
import org.openhab.habdroid.core.notifications.GoogleCloudMessageConnector;
import org.openhab.habdroid.core.notifications.NotificationSettings;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.model.thing.ThingType;
import org.openhab.habdroid.ui.drawer.OpenHABDrawerAdapter;
import org.openhab.habdroid.ui.drawer.OpenHABDrawerItem;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.MySyncHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import de.duenndns.ssl.MTMDecision;
import de.duenndns.ssl.MemorizingResponder;
import de.duenndns.ssl.MemorizingTrustManager;

public class OpenHABMainActivity extends AppCompatActivity implements OnWidgetSelectedListener,
        OpenHABTrackerReceiver, MemorizingResponder {

    private abstract class DefaultHttpResponseHandler extends AsyncHttpResponseHandler {


        @Override
        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
            setProgressIndicatorVisible(false);
            if (error instanceof HttpResponseException) {
                switch (((HttpResponseException) error).getStatusCode()) {
                    case 401:
                        showAlertDialog(getString(R.string.error_authentication_failed));
                        break;
                    default:
                        showError(error.getMessage());
                }
            } else if (error instanceof HttpHostConnectException) {
                Log.e(TAG, "Error connecting to host");
                showError(error.getMessage());
            } else if (error instanceof java.net.UnknownHostException) {
                Log.e(TAG, "Unable to resolve hostname");
                showError(error.getMessage());
            } else if (error instanceof SSLHandshakeException) {
                showError(getString(R.string.error_connection_sslhandshake_failed));
            } else {
                Log.e(TAG, error.getClass().toString());
            }
        }

        private void showError(String message) {
            if (message != null) {
                Log.e(TAG, message);
                showAlertDialog(message);
            } else {
                showAlertDialog(getString(R.string.error_connection_failed));
            }
        }
    }
    // GCM Registration expiration
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;
    // Logging TAG
    private static final String TAG = OpenHABMainActivity.class.getSimpleName();
    // Activities request codes
    private static final int SETTINGS_REQUEST_CODE = 1002;
    private static final int WRITE_NFC_TAG_REQUEST_CODE = 1003;
    private static final int INFO_REQUEST_CODE = 1004;
    // Drawer item codes
    private static final int DRAWER_NOTIFICATIONS = 100;
    private static final int DRAWER_BINDINGS = 101;
    private static final int DRAWER_INBOX = 102;
    // Loopj
//    private static MyAsyncHttpClient mAsyncHttpClient;
    private static AsyncHttpClient mAsyncHttpClient = new AsyncHttpClient();
    // Base URL of current openHAB connection
    private String openHABBaseUrl = "http://demo.openhab.org:8080/";
    // openHAB username
    private String openHABUsername = "";
    // openHAB password
    private String openHABPassword = "";
    // openHAB Bonjour service name
    private String openHABServiceType;
    // view pager for widgetlist fragments
    private OpenHABViewPager pager;
    // view pager adapter for widgetlist fragments
    private OpenHABFragmentPagerAdapter pagerAdapter;
    // root URL of the current sitemap
    private String sitemapRootUrl;
    // A fragment which retains it's state through configuration changes to keep the current state of the app
    private StateRetainFragment stateFragment;
    // preferences
    private SharedPreferences mSettings;
    // OpenHAB tracker
    private OpenHABTracker mOpenHABTracker;
    // Progress dialog
    private ProgressDialog mProgressDialog;
    // If Voice Recognition is enabled
    private boolean mVoiceRecognitionEnabled = false;
    // If openHAB discovery is enabled
    private boolean mServiceDiscoveryEnabled = true;
    // NFC Launch data
    private String mNfcData;
    // Pending NFC page
    private String mPendingNfcPage;
    // Toolbar / Actionbar
    private Toolbar mToolbar;
    // Drawer Layout
    private DrawerLayout mDrawerLayout;
    // Drawer Toggler
    private ActionBarDrawerToggle mDrawerToggle;
    // Google Cloud Messaging
    private GoogleCloudMessaging mGcm;
    private OpenHABDrawerAdapter mDrawerAdapter;
    private ArrayList<OpenHABSitemap> mSitemapList;
    private NetworkConnectivityInfo mStartedWithNetworkConnectivityInfo;
    private int mOpenHABVersion;
    private List<OpenHABDrawerItem> mDrawerItemList;
    private ProgressBar mProgressBar;
    private Boolean mIsMyOpenHAB = false;
    private NotificationSettings mNotifySettings = null;

    public static String GCM_SENDER_ID;

    /*
     *Daydreaming gets us into a funk when in fullscreen, this allows us to
     *reset ourselves to fullscreen.
     * @author Dan Cunningham
     */
    private BroadcastReceiver dreamReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("INTENTFILTER", "Recieved intent: " + intent.toString());
            checkFullscreen();
        }
    };

    public static AsyncHttpClient getAsyncHttpClient() {
        return mAsyncHttpClient;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");

        // Set default values, false means do it one time during the very first launch
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set non-persistent HABDroid version preference to current version from application package
        try {
            Log.d(TAG, "App version = " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(Constants.PREFERENCE_APPVERSION,
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName).commit();
        } catch (PackageManager.NameNotFoundException e1) {
            Log.d(TAG, e1.getMessage());
        }

        checkDiscoveryPermissions();
        checkVoiceRecognition();

        // initialize loopj async http client
        mAsyncHttpClient = new MyAsyncHttpClient(this);

        // Set the theme to one from preferences
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        // Disable screen timeout if set in preferences
        if (mSettings.getBoolean(Constants.PREFERENCE_SCREENTIMEROFF, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Fetch openHAB service type name from strings.xml
        openHABServiceType = getString(R.string.openhab_service_type);

        // Get username/password from preferences
        openHABUsername = mSettings.getString(Constants.PREFERENCE_USERNAME, null);
        openHABPassword = mSettings.getString(Constants.PREFERENCE_PASSWORD, null);
        mAsyncHttpClient.setBasicAuth(openHABUsername, openHABPassword, true);
        mAsyncHttpClient.setTimeout(30000);

        if (!BuildConfig.IS_DEVELOPER) {
            Util.initCrittercism(getApplicationContext(), "5117659f59e1bd4ba9000004");
        }

        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        if (!BuildConfig.IS_DEVELOPER) {
            ((HABDroid) getApplication()).getTracker(HABDroid.TrackerName.APP_TRACKER);
        }

        setContentView(R.layout.activity_main);

        setupToolbar();
        setupDrawer();
        gcmRegisterBackground();
        setupPager();

        MemorizingTrustManager.setResponder(this);

        // Check if we have openHAB page url in saved instance state?
        if (savedInstanceState != null) {
            openHABBaseUrl = savedInstanceState.getString("openHABBaseUrl");
            sitemapRootUrl = savedInstanceState.getString("sitemapRootUrl");
            mStartedWithNetworkConnectivityInfo = savedInstanceState.getParcelable("startedWithNetworkConnectivityInfo");
            mOpenHABVersion = savedInstanceState.getInt("openHABVersion");
            mSitemapList = savedInstanceState.getParcelableArrayList("sitemapList");
            pagerAdapter.setOpenHABBaseUrl(openHABBaseUrl);
        }

        if (mSitemapList == null) {
            mSitemapList = new ArrayList<>();
        }

        if (getIntent() != null) {
            Log.d(TAG, "Intent != null");
            if (getIntent().getAction() != null) {
                Log.d(TAG, "Intent action = " + getIntent().getAction());
                if (getIntent().getAction().equals("android.nfc.action.NDEF_DISCOVERED")) {
                    Log.d(TAG, "This is NFC action");
                    if (getIntent().getDataString() != null) {
                        Log.d(TAG, "NFC data = " + getIntent().getDataString());
                        mNfcData = getIntent().getDataString();
                    }
                } else if (getIntent().getAction().equals("org.openhab.notification.selected")) {
                    onNotificationSelected(getIntent());
                } else if (getIntent().getAction().equals("android.intent.action.VIEW")) {
                    Log.d(TAG, "This is URL Action");
                    mNfcData = getIntent().getDataString();
                }
            }
        }

        if (isFullscreenEnabled()) {
            registerReceiver(dreamReceiver, new IntentFilter("android.intent.action.DREAMING_STARTED"));
            registerReceiver(dreamReceiver, new IntentFilter("android.intent.action.DREAMING_STOPPED"));
            checkFullscreen();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, ((Object) this).getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        if (NfcAdapter.getDefaultAdapter(this) != null)
            NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, null, null);
        if (!TextUtils.isEmpty(mNfcData)) {
            Log.d(TAG, "We have NFC data from launch");
        }
        pagerAdapter.setColumnsNumber(getResources().getInteger(R.integer.pager_columns));
        FragmentManager fm = getSupportFragmentManager();
        stateFragment = (StateRetainFragment) fm.findFragmentByTag("stateFragment");
        // If state fragment doesn't exist (which means fresh start of the app)
        // or if state fragment returned 0 fragments (this happens sometimes and we don't yet
        // know why, so this is a workaround
        // start over the whole process
        if (stateFragment == null || stateFragment.getFragmentList().size() == 0) {
            stateFragment = null;
            stateFragment = new StateRetainFragment();
            fm.beginTransaction().add(stateFragment, "stateFragment").commit();
            mOpenHABTracker = new OpenHABTracker(this, openHABServiceType, mServiceDiscoveryEnabled);
            mStartedWithNetworkConnectivityInfo = NetworkConnectivityInfo.currentNetworkConnectivityInfo(this);
            mOpenHABTracker.start();
            // If state fragment exists and contains something then just restore the fragments
        } else {
            Log.d(TAG, "State fragment found");
            // If connectivity type changed while we were in background
            // Restart the whole process
            // TODO: this must be refactored to remove duplicate code!
            if (!NetworkConnectivityInfo.currentNetworkConnectivityInfo(this).equals(mStartedWithNetworkConnectivityInfo)) {
                Log.d(TAG, "Connectivity type changed while I was out, or zero fragments found, need to restart");
                // Clean up any existing fragments
                pagerAdapter.clearFragmentList();
                stateFragment.getFragmentList().clear();
                stateFragment = null;
                // Clean up title
                this.setTitle(R.string.app_name);
                stateFragment = new StateRetainFragment();
                fm.beginTransaction().add(stateFragment, "stateFragment").commit();
                mOpenHABTracker = new OpenHABTracker(this, openHABServiceType, mServiceDiscoveryEnabled);
                mStartedWithNetworkConnectivityInfo = NetworkConnectivityInfo.currentNetworkConnectivityInfo(this);
                mOpenHABTracker.start();
                return;
            }
            pagerAdapter.setFragmentList(stateFragment.getFragmentList());
            Log.d(TAG, String.format("Loaded %d fragments", stateFragment.getFragmentList().size()));
            pager.setCurrentItem(stateFragment.getCurrentPage());
            Log.d(TAG, String.format("Loaded current page = %d", stateFragment.getCurrentPage()));
        }
        if (!TextUtils.isEmpty(mPendingNfcPage)) {
            openNFCPageIfPending();
        }

        checkFullscreen();
    }

    /**
     * Overriding onStart to enable Google Analytics stats collection
     */
    @Override
    public void onStart() {
        super.onStart();
        // Start activity tracking via Google Analytics
        if (!BuildConfig.IS_DEVELOPER) {
            GoogleAnalytics.getInstance(this).reportActivityStart(this);
        }
    }

    /**
     * Overriding onStop to enable Google Analytics stats collection
     */
    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        // Stop activity tracking via Google Analytics
        if (!BuildConfig.IS_DEVELOPER) {
            GoogleAnalytics.getInstance(this).reportActivityStop(this);
        }
        if (mOpenHABTracker != null) {
            mOpenHABTracker.stop();
        }
    }

    private void setupToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.openhab_toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // ProgressBar layout params inside the toolbar have to be done programmatically
        // because it doesn't work through layout file :-(
        mProgressBar = (ProgressBar) mToolbar.findViewById(R.id.toolbar_progress_bar);
        mProgressBar.setLayoutParams(new Toolbar.LayoutParams(Gravity.END | Gravity.CENTER_VERTICAL));
        setProgressIndicatorVisible(true);
    }

    private void setupDrawer() {

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        final ListView drawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerToggle = new ActionBarDrawerToggle(OpenHABMainActivity.this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                loadSitemapList(openHABBaseUrl);
                super.onDrawerOpened(drawerView);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mDrawerItemList = new ArrayList<>();
        mDrawerAdapter = new OpenHABDrawerAdapter(this, R.layout.openhabdrawer_sitemap_item, mDrawerItemList);
        mDrawerAdapter.setOpenHABUsername(openHABUsername);
        mDrawerAdapter.setOpenHABPassword(openHABPassword);
        drawerList.setAdapter(mDrawerAdapter);
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int item, long l) {
                Log.d(TAG, "Drawer selected item " + String.valueOf(item));
                if (mDrawerItemList != null && mDrawerItemList.get(item).getItemType() == OpenHABDrawerItem.DrawerItemType.SITEMAP_ITEM) {
                    Log.d(TAG, "This is sitemap " + mDrawerItemList.get(item).getSiteMap().getLink());
                    mDrawerLayout.closeDrawers();
                    openSitemap(mDrawerItemList.get(item).getSiteMap().getHomepageLink());
                } else {
                    Log.d(TAG, "This is not sitemap");
                    if (mDrawerItemList.get(item).getTag() == DRAWER_NOTIFICATIONS) {
                        Log.d(TAG, "Notifications selected");
                        mDrawerLayout.closeDrawers();
                        OpenHABMainActivity.this.openNotifications();
                    } else if (mDrawerItemList.get(item).getTag() == DRAWER_BINDINGS) {
                        Log.d(TAG, "Bindings selected");
                        mDrawerLayout.closeDrawers();
                        OpenHABMainActivity.this.openBindings();
                    } else if (mDrawerItemList.get(item).getTag() == DRAWER_INBOX) {
                        Log.d(TAG, "Inbox selected");
                        mDrawerLayout.closeDrawers();
                        OpenHABMainActivity.this.openDiscoveryInbox();
                    }
                }
            }
        });
        loadDrawerItems();
    }

    private void setupPager() {
        pagerAdapter = new OpenHABFragmentPagerAdapter(getSupportFragmentManager());
        pagerAdapter.setColumnsNumber(getResources().getInteger(R.integer.pager_columns));
        pagerAdapter.setOpenHABUsername(openHABUsername);
        pagerAdapter.setOpenHABPassword(openHABPassword);
        pager = (OpenHABViewPager) findViewById(R.id.pager);
        pager.setScrollDurationFactor(2.5);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(pagerAdapter);
        pager.addOnPageChangeListener(pagerAdapter);
    }

    public void openNFCPageIfPending() {
        int possiblePosition = pagerAdapter.getPositionByUrl(mPendingNfcPage);
        // If yes, then just switch to this page
        if (possiblePosition >= 0) {
            pager.setCurrentItem(possiblePosition);
            // If not, then open this page as new one
        } else {
            pagerAdapter.openPage(mPendingNfcPage);
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
        mPendingNfcPage = null;
    }

    public void onOpenHABTracked(String baseUrl, String message) {
        if (message != null) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
        openHABBaseUrl = baseUrl;
        mDrawerAdapter.setOpenHABBaseUrl(openHABBaseUrl);
        pagerAdapter.setOpenHABBaseUrl(openHABBaseUrl);
        if (!TextUtils.isEmpty(mNfcData)) {
            onNfcTag(mNfcData);
            openNFCPageIfPending();
        } else {
            mAsyncHttpClient.get(baseUrl + "rest/bindings", new TextHttpResponseHandler() {
                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                    mOpenHABVersion = 1;
                    Log.d(TAG, "openHAB version 1");
                    mAsyncHttpClient.addHeader("Accept", "application/xml");
                    selectSitemap(openHABBaseUrl, false);
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseString) {
                    mOpenHABVersion = 2;
                    Log.d(TAG, "openHAB version 2");
                    selectSitemap(openHABBaseUrl, false);
                }
            });
        }
    }

    public void onError(String error) {
        Toast.makeText(getApplicationContext(), error,
                Toast.LENGTH_LONG).show();
    }

    public void onBonjourDiscoveryStarted() {
        mProgressDialog = ProgressDialog.show(this, "",
                getString(R.string.info_discovery), true);
    }

    public void onBonjourDiscoveryFinished() {
        try {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        } catch (Exception e) {
            // This is to catch "java.lang.IllegalArgumentException: View not attached to window manager"
            // exception which happens if user quited app during discovery
        }
    }

    private void loadSitemapList(String baseUrl) {
        Log.d(TAG, "Loading sitemap list from " + baseUrl + "rest/sitemaps");
        setProgressIndicatorVisible(true);
        mAsyncHttpClient.get(baseUrl + "rest/sitemaps", new DefaultHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                setProgressIndicatorVisible(false);
                mSitemapList.clear();
                // If openHAB's version is 1, get sitemap list from XML
                if (mOpenHABVersion == 1) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder builder = dbf.newDocumentBuilder();
                        Document sitemapsXml = builder.parse(new ByteArrayInputStream(responseBody));
                        mSitemapList.addAll(Util.parseSitemapList(sitemapsXml));
                    } catch (ParserConfigurationException | SAXException | IOException e) {
                        e.printStackTrace();
                    }
                    // Later versions work with JSON
                } else {
                    try {
                        String jsonString = new String(responseBody, "UTF-8");
                        JSONArray jsonArray = new JSONArray(jsonString);
                        mSitemapList.addAll(Util.parseSitemapList(jsonArray));
                        Log.d(TAG, jsonArray.toString());
                    } catch (UnsupportedEncodingException | JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (mSitemapList.size() == 0) {
                    return;
                }
                loadDrawerItems();
            }
        });
    }

    /**
     * Get sitemaps from openHAB, if user already configured preffered sitemap
     * just open it. If no preffered sitemap is configured - let user select one.
     *
     * @param baseUrl an absolute base URL of openHAB to open
     * @return void
     */

    private void selectSitemap(final String baseUrl, final boolean forceSelect) {
        Log.d(TAG, "Loading sitemap list from " + baseUrl + "rest/sitemaps");
        setProgressIndicatorVisible(true);
        mAsyncHttpClient.get(baseUrl + "rest/sitemaps", new DefaultHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                Log.d(TAG, new String(responseBody));
                setProgressIndicatorVisible(false);
                mSitemapList.clear();
                // If openHAB's version is 1, get sitemap list from XML
                if (mOpenHABVersion == 1) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder builder = dbf.newDocumentBuilder();
                        Document sitemapsXml = builder.parse(new ByteArrayInputStream(responseBody));
                        mSitemapList.addAll(Util.parseSitemapList(sitemapsXml));
                    } catch (ParserConfigurationException | SAXException | IOException e) {
                        e.printStackTrace();
                    }
                    // Later versions work with JSON
                } else {
                    try {
                        String jsonString = new String(responseBody, "UTF-8");
                        JSONArray jsonArray = new JSONArray(jsonString);
                        mSitemapList.addAll(Util.parseSitemapList(jsonArray));
                        Log.d(TAG, jsonArray.toString());
                    } catch (UnsupportedEncodingException | JSONException e) {
                        e.printStackTrace();
                    }
                }
                // Now work with sitemaps list
                if (mSitemapList.size() == 0) {
                    // Got an empty sitemap list!
                    Log.e(TAG, "openHAB returned empty sitemap list");
                    showAlertDialog(getString(R.string.error_empty_sitemap_list));
                    return;
                }
                loadDrawerItems();
                // If we are forced to do selection, just open selection dialog
                if (forceSelect) {
                    showSitemapSelectionDialog(mSitemapList);
                } else {
                    // Check if we have a sitemap configured to use
                    SharedPreferences settings =
                            PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
                    String configuredSitemap = settings.getString(Constants.PREFERENCE_SITEMAP, "");
                    // If we have sitemap configured
                    if (configuredSitemap.length() > 0) {
                        // Configured sitemap is on the list we got, open it!
                        if (Util.sitemapExists(mSitemapList, configuredSitemap)) {
                            Log.d(TAG, "Configured sitemap is on the list");
                            OpenHABSitemap selectedSitemap = Util.getSitemapByName(mSitemapList, configuredSitemap);
                            openSitemap(selectedSitemap.getHomepageLink());
                            // Configured sitemap is not on the list we got!
                        } else {
                            Log.d(TAG, "Configured sitemap is not on the list");
                            if (mSitemapList.size() == 1) {
                                Log.d(TAG, "Got only one sitemap");
                                SharedPreferences.Editor preferencesEditor = settings.edit();
                                preferencesEditor.putString(Constants.PREFERENCE_SITEMAP, mSitemapList.get(0).getName());
                                preferencesEditor.commit();
                                openSitemap(mSitemapList.get(0).getHomepageLink());
                            } else {
                                Log.d(TAG, "Got multiply sitemaps, user have to select one");
                                showSitemapSelectionDialog(mSitemapList);
                            }
                        }
                        // No sitemap is configured to use
                    } else {
                        // We got only one single sitemap from openHAB, use it
                        if (mSitemapList.size() == 1) {
                            Log.d(TAG, "Got only one sitemap");
                            SharedPreferences.Editor preferencesEditor = settings.edit();
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP, mSitemapList.get(0).getName());
                            preferencesEditor.commit();
                            openSitemap(mSitemapList.get(0).getHomepageLink());
                        } else {
                            Log.d(TAG, "Got multiply sitemaps, user have to select one");
                            showSitemapSelectionDialog(mSitemapList);
                        }
                    }
                }
            }
        });
    }

    private void showSitemapSelectionDialog(final List<OpenHABSitemap> sitemapList) {
        Log.d(TAG, "Opening sitemap selection dialog");
        final List<String> sitemapNameList = new ArrayList<String>();
        ;
        for (int i = 0; i < sitemapList.size(); i++) {
            sitemapNameList.add(sitemapList.get(i).getName());
        }
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(OpenHABMainActivity.this);
        dialogBuilder.setTitle(getString(R.string.mainmenu_openhab_selectsitemap));
        try {
            dialogBuilder.setItems(sitemapNameList.toArray(new CharSequence[sitemapNameList.size()]),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            Log.d(TAG, "Selected sitemap " + sitemapNameList.get(item));
                            SharedPreferences settings =
                                    PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
                            SharedPreferences.Editor preferencesEditor = settings.edit();
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP, sitemapList.get(item).getName());
                            preferencesEditor.commit();
                            openSitemap(sitemapList.get(item).getHomepageLink());
                        }
                    }).show();
        } catch (WindowManager.BadTokenException e) {
            Crittercism.logHandledException(e);
        }
    }

    public void openNotifications() {
        if (this.pagerAdapter != null) {
            pagerAdapter.openNotifications();
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
    }

    public void openBindings() {
        if (this.pagerAdapter != null) {
            pagerAdapter.openBindings();
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
    }

    public void openDiscovery() {
        if (this.pagerAdapter != null) {
            pagerAdapter.openDiscovery();
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
    }

    public void openDiscoveryInbox() {
        if (this.pagerAdapter != null) {
            pagerAdapter.openDiscoveryInbox();
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
    }

    public void openBindingThingTypes(ArrayList<ThingType> thingTypes) {
        if (this.pagerAdapter != null) {
            pagerAdapter.openBindingThingTypes(thingTypes);
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
    }

    private void openSitemap(String sitemapUrl) {
        Log.i(TAG, "Opening sitemap at " + sitemapUrl);
        sitemapRootUrl = sitemapUrl;
        pagerAdapter.clearFragmentList();
        pagerAdapter.openPage(sitemapRootUrl);
        pager.setCurrentItem(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.mainmenu_voice_recognition).setVisible(mVoiceRecognitionEnabled);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        //clicking the back navigation arrow
        if (pager.getCurrentItem() > 0 && item.getItemId() == android.R.id.home) {
            onBackPressed();
            return false;
        }

        //clicking the hamburger menu
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        //menu items
        switch (item.getItemId()) {
            case R.id.mainmenu_openhab_preferences:
                Intent settingsIntent = new Intent(this.getApplicationContext(), OpenHABPreferencesActivity.class);
                startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE);
                Util.overridePendingTransition(this, false);
                return true;
            case R.id.mainmenu_openhab_selectsitemap:
                SharedPreferences settings =
                        PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
                SharedPreferences.Editor preferencesEditor = settings.edit();
                preferencesEditor.putString(Constants.PREFERENCE_SITEMAP, "");
                preferencesEditor.commit();
                selectSitemap(openHABBaseUrl, true);
                return true;
            case R.id.mainmenu_openhab_clearcache:
                Log.d(TAG, "Restarting");
                // Get launch intent for application
                Intent restartIntent = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getBaseContext().getPackageName());
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                // Finish current activity
                finish();
                WebImageCache cache = new WebImageCache(getBaseContext());
                cache.clear();
                // Start launch activity
                startActivity(restartIntent);
                // Start launch activity
                return true;
            case R.id.mainmenu_openhab_writetag:
                Intent writeTagIntent = new Intent(this.getApplicationContext(), OpenHABWriteTagActivity.class);
                // TODO: get current display page url, which? how? :-/
                if (pagerAdapter.getFragment(pager.getCurrentItem()) instanceof OpenHABWidgetListFragment) {
                    OpenHABWidgetListFragment currentFragment = (OpenHABWidgetListFragment) pagerAdapter.getFragment(pager.getCurrentItem());
                    if (currentFragment != null) {
                        writeTagIntent.putExtra("sitemapPage", currentFragment.getDisplayPageUrl());
                        startActivityForResult(writeTagIntent, WRITE_NFC_TAG_REQUEST_CODE);
                        Util.overridePendingTransition(this, false);
                    }
                }
                return true;
            case R.id.mainmenu_openhab_info:

                Bundle bundle = new Bundle();
                bundle.putString(OpenHABVoiceService.OPENHAB_BASE_URL_EXTRA, openHABBaseUrl);
                bundle.putString("username", openHABUsername);
                bundle.putString("password", openHABPassword);
                bundle.putInt("openHABVersion", mOpenHABVersion);

                FragmentManager fm = getSupportFragmentManager();
                Fragment openHabInfo = new OpenHABInfoFragment();

                openHabInfo.setArguments(bundle);
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(openHabInfo, "openHabTag");
                ft.commit();
                return true;
            case R.id.mainmenu_voice_recognition:
                launchVoiceRecognition();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, String.format("onActivityResult requestCode = %d, resultCode = %d", requestCode, resultCode));
        switch (requestCode) {
            case SETTINGS_REQUEST_CODE:
                // Restart app after preferences
                Log.d(TAG, "Restarting after settings");
                // Get launch intent for application
                Intent restartIntent = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getBaseContext().getPackageName());
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                // Finish current activity
                finish();
                // Start launch activity
                startActivity(restartIntent);
                break;
            case WRITE_NFC_TAG_REQUEST_CODE:
                Log.d(TAG, "Got back from Write NFC tag");
                break;
            default:
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onSaveInstanceState");
        // Save opened framents into state retaining fragment (I love Google! :-)
        Log.d(TAG, String.format("Saving %d fragments", pagerAdapter.getFragmentList().size()));
        Log.d(TAG, String.format("Saving current page = %d", pager.getCurrentItem()));
        stateFragment.setFragmentList(pagerAdapter.getFragmentList());
        stateFragment.setCurrentPage(pager.getCurrentItem());
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        savedInstanceState.putString("openHABBaseUrl", openHABBaseUrl);
        savedInstanceState.putString("sitemapRootUrl", sitemapRootUrl);
        savedInstanceState.putInt("currentFragment", pager.getCurrentItem());
        savedInstanceState.putParcelable("startedWithNetworkConnectivityInfo", mStartedWithNetworkConnectivityInfo);
        savedInstanceState.putInt("openHABVersion", mOpenHABVersion);
        savedInstanceState.putParcelableArrayList("sitemapList", mSitemapList);
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * This method is called when activity receives a new intent while running
     */
    @Override
    public void onNewIntent(Intent newIntent) {
        if (newIntent.getAction() != null) {
            Log.d(TAG, "New intent received = " + newIntent.getAction());
            if (newIntent.getAction().equals("android.nfc.action.NDEF_DISCOVERED")) {
                Log.d(TAG, "This is NFC action");
                if (newIntent.getDataString() != null) {
                    Log.d(TAG, "Action data = " + newIntent.getDataString());
                    onNfcTag(newIntent.getDataString());
                }
            } else if (newIntent.getAction().equals("org.openhab.notification.selected")) {
                onNotificationSelected(newIntent);
            } else if (newIntent.getAction().equals("android.intent.action.VIEW")) {
                Log.d(TAG, "This is URL Action");
                onNfcTag(newIntent.getDataString());
            }
        }
    }

    private void onNotificationSelected(Intent intent) {
        Log.d(TAG, "Notification was selected");
        if (intent.hasExtra("notificationId")) {
            Log.d(TAG, String.format("Notification id = %d",
                    intent.getExtras().getInt("notificationId")));
            // Make a fake broadcast intent to hide intent on other devices
            Intent deleteIntent = new Intent(this, NotificationDeletedBroadcastReceiver.class);
            deleteIntent.setAction("org.openhab.notification.deleted");
            deleteIntent.putExtra("notificationId", intent.getExtras().getInt("notificationId"));
            sendBroadcast(deleteIntent);
        }
    }

    /**
     * This method processes new intents generated by NFC subsystem
     *
     * @param nfcData - a data which NFC subsystem got from the NFC tag
     */
    public void onNfcTag(String nfcData) {
        Log.d(TAG, "onNfcTag()");
        Uri openHABURI = Uri.parse(nfcData);
        Log.d(TAG, "NFC Scheme = " + openHABURI.getScheme());
        Log.d(TAG, "NFC Host = " + openHABURI.getHost());
        Log.d(TAG, "NFC Path = " + openHABURI.getPath());
        String nfcItem = openHABURI.getQueryParameter("item");
        String nfcCommand = openHABURI.getQueryParameter("command");
        String nfcItemType = openHABURI.getQueryParameter("itemType");
        // If there is no item parameter it means tag contains only sitemap page url
        if (TextUtils.isEmpty(nfcItem)) {
            Log.d(TAG, "This is a sitemap tag without parameters");
            // Form the new sitemap page url
            String newPageUrl = openHABBaseUrl + "rest/sitemaps" + openHABURI.getPath();
            // Check if we have this page in stack?
            mPendingNfcPage = newPageUrl;
        } else {
            Log.d(TAG, "Target item = " + nfcItem);
            sendItemCommand(nfcItem, nfcCommand);
            // if mNfcData is not empty, this means we were launched with NFC touch
            // and thus need to autoexit after an item action
            if (!TextUtils.isEmpty(mNfcData))
                finish();
        }
        mNfcData = "";
    }

    public void sendItemCommand(String itemName, String command) {
        try {
            StringEntity se = new StringEntity(command, "UTF-8");
            mAsyncHttpClient.post(this, openHABBaseUrl + "rest/items/" + itemName, se, "text/plain;charset=UTF-8", new TextHttpResponseHandler() {
                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable error) {
                    Log.e(TAG, "Got command error " + error.getMessage());
                    if (responseString != null)
                        Log.e(TAG, "Error response = " + responseString);
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseString) {
                    Log.d(TAG, "Command was sent successfully");
                }
            });
        } catch (RuntimeException e) {
            if (e.getMessage() != null)
                Log.e(TAG, e.getMessage());
        }
    }

    public void onWidgetSelectedListener(OpenHABLinkedPage linkedPage, OpenHABWidgetListFragment source) {
        Log.i(TAG, "Got widget link = " + linkedPage.getLink());
        Log.i(TAG, String.format("Link came from fragment on position %d", source.getPosition()));
        pagerAdapter.openPage(linkedPage.getLink(), source.getPosition() + 1);
        pager.setCurrentItem(pagerAdapter.getCount() - 1);
        setTitle(linkedPage.getTitle());
        //set the drawer icon to a back arrow when not on the rook menu
        mDrawerToggle.setDrawerIndicatorEnabled(pager.getCurrentItem() == 0);
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, String.format("onBackPressed() I'm at the %d page", pager.getCurrentItem()));
        if (pager.getCurrentItem() == 0) {
            //in fullscreen don't continue back which would exit the app
            if (!isFullscreenEnabled()) {
                super.onBackPressed();
            }
        } else {
            pager.setCurrentItem(pager.getCurrentItem() - 1, true);
            setTitle(pagerAdapter.getPageTitle(pager.getCurrentItem()));
            //set the drawer icon back to to hamburger menu if on the root menu
            mDrawerToggle.setDrawerIndicatorEnabled(pager.getCurrentItem() == 0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.v(TAG, "KeyDown: " + event.toString());
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (pagerAdapter.getFragment(pager.getCurrentItem()) instanceof OpenHABWidgetListFragment) {
                OpenHABWidgetListFragment currentFragment = (OpenHABWidgetListFragment) pagerAdapter.getFragment(pager.getCurrentItem());
                if (currentFragment != null)
                    return currentFragment.onVolumeDown();
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (pagerAdapter.getFragment(pager.getCurrentItem()) instanceof OpenHABWidgetListFragment) {
                OpenHABWidgetListFragment currentFragment = (OpenHABWidgetListFragment) pagerAdapter.getFragment(pager.getCurrentItem());
                if (currentFragment != null)
                    return currentFragment.onVolumeUp();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.v(TAG, "KeyUp: " + event.toString());
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (pagerAdapter.getFragment(pager.getCurrentItem()) instanceof OpenHABWidgetListFragment) {
                OpenHABWidgetListFragment currentFragment = (OpenHABWidgetListFragment) pagerAdapter.getFragment(pager.getCurrentItem());
                if (currentFragment != null && currentFragment.isVolumeHandled())
                    return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    protected void setProgressIndicatorVisible(boolean visible) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void launchVoiceRecognition() {
        Intent callbackIntent = new Intent(this, OpenHABVoiceService.class);
        callbackIntent.putExtra(OpenHABVoiceService.OPENHAB_BASE_URL_EXTRA, openHABBaseUrl);
        PendingIntent openhabPendingIntent = PendingIntent.getService(this, 0, callbackIntent, 0);

        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // Display an hint to the user about what he should say.
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.info_voice_input));
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, openhabPendingIntent);

        try {
            startActivity(speechIntent);
        } catch(ActivityNotFoundException e) {
            // Speech not installed?
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://market.android.com/details?id=com.google.android.voicesearch"));
            startActivity(browserIntent);
        }
    }

    private void showAlertDialog(String alertMessage) {
        if (this.isFinishing())
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABMainActivity.this);
        builder.setMessage(alertMessage)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void showCertificateDialog(final int decisionId, String certMessage) {
        if (this.isFinishing())
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABMainActivity.this);
        builder.setMessage(certMessage)
                .setTitle(R.string.mtm_accept_cert);
        builder.setPositiveButton(R.string.mtm_decision_always, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d(TAG, "User decided to always accept unknown certificate");
//                MemorizingTrustManager.interactResult(decisionId, MTMDecision.DECISION_ALWAYS);
                sendMTMDecision(decisionId, MTMDecision.DECISION_ALWAYS);
            }
        });
        builder.setNeutralButton(R.string.mtm_decision_once, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d(TAG, "User decided to accept unknown certificate once");
//                MemorizingTrustManager.interactResult(decisionId, MTMDecision.DECISION_ONCE);
                sendMTMDecision(decisionId, MTMDecision.DECISION_ONCE);
            }
        });
        builder.setNegativeButton(R.string.mtm_decision_abort, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d(TAG, "User decided to abort unknown certificate");
//                MemorizingTrustManager.interactResult(decisionId, MTMDecision.DECISION_ABORT);
                sendMTMDecision(decisionId, MTMDecision.DECISION_ABORT);
            }
        });
        AlertDialog certAlert = builder.create();
        certAlert.show();
    }

    void sendMTMDecision(int decisionId, int decision) {
        Log.d(TAG, "Sending decision to MTM");
        Intent i = new Intent(MemorizingTrustManager.DECISION_INTENT + "/" + getPackageName());
        i.putExtra(MemorizingTrustManager.DECISION_INTENT_ID, decisionId);
        i.putExtra(MemorizingTrustManager.DECISION_INTENT_CHOICE, decision);
        sendBroadcast(i);
    }

    public void checkVoiceRecognition() {
        // Check if voice recognition is present
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0) {
//            speakButton.setEnabled(false);
//            speakButton.setText("Voice recognizer not present");
            Toast.makeText(this, "Voice recognizer not present, voice recognition disabled",
                    Toast.LENGTH_SHORT).show();
        } else {
            mVoiceRecognitionEnabled = true;
        }
    }

    public void checkDiscoveryPermissions() {
        // Check if we got all needed permissions
        PackageManager pm = getPackageManager();
        if (!(pm.checkPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED)) {
            showAlertDialog(getString(R.string.erorr_no_wifi_mcast_permission));
            mServiceDiscoveryEnabled = false;
        }
        if (!(pm.checkPermission(Manifest.permission.ACCESS_WIFI_STATE, getPackageName()) == PackageManager.PERMISSION_GRANTED)) {
            showAlertDialog(getString(R.string.erorr_no_wifi_state_permission));
            mServiceDiscoveryEnabled = false;
        }

    }

    public void makeDecision(int decisionId, String certMessage) {
        Log.d(TAG, String.format("MTM is asking for decision on id = %d", decisionId));
        if (mSettings.getBoolean(Constants.PREFERENCE_SSLCERT, false))
            MemorizingTrustManager.interactResult(decisionId, MTMDecision.DECISION_ONCE);
        else
            showCertificateDialog(decisionId, certMessage);
    }

    public String getOpenHABBaseUrl() {
        return openHABBaseUrl;
    }

    public void setOpenHABBaseUrl(String openHABBaseUrl) {
        this.openHABBaseUrl = openHABBaseUrl;
    }

    public String getOpenHABUsername() {
        return openHABUsername;
    }

    public void setOpenHABUsername(String openHABUsername) {
        this.openHABUsername = openHABUsername;
    }

    public String getOpenHABPassword() {
        return openHABPassword;
    }

    public void setOpenHABPassword(String openHABPassword) {
        this.openHABPassword = openHABPassword;
    }

    public int getOpenHABVersion() {
        return this.mOpenHABVersion;
    }

    private void gcmRegisterBackground() {
        Crittercism.setUsername(openHABUsername);
        OpenHABMainActivity.GCM_SENDER_ID = null;
        // if no notification settings can be constructed, no GCM registration can be made.
        if (getNotificationSettings() == null)
            return;

        if (mGcm == null)
            mGcm = GoogleCloudMessaging.getInstance(getApplicationContext());

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                GoogleCloudMessageConnector connector =
                        new GoogleCloudMessageConnector(getNotificationSettings(), deviceId, mGcm);

                if (connector.register()) {
                    OpenHABMainActivity.GCM_SENDER_ID = getNotificationSettings().getSenderId();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String regId) {}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    /**
     * Returns the notification settings object
     * @return
     */
    public NotificationSettings getNotificationSettings() {
        if (mNotifySettings == null) {
            // We need settings
            if (mSettings == null)
                return null;

            // We need remote URL, username and password, without them we can't connect to openhab-cloud
            String remoteUrl = mSettings.getString(Constants.PREFERENCE_ALTURL, null);
            if (TextUtils.isEmpty(remoteUrl) || TextUtils.isEmpty(openHABUsername) || TextUtils.isEmpty(openHABPassword)) {
                Log.d(TAG, "Remote URL, username or password are empty, no GCM registration will be made");
                return null;
            }
            final URL baseUrl;
            try {
                baseUrl = new URL(remoteUrl);
            } catch(MalformedURLException ex) {
                Log.d(TAG, "Could not parse the baseURL to an URL: " + ex.getMessage());
                return null;
            }
            SyncHttpClient httpClient = new MySyncHttpClient(this);
            httpClient.setBasicAuth(getOpenHABUsername(), getOpenHABPassword(), true);
            httpClient.setTimeout(30000);
            mNotifySettings = new NotificationSettings(baseUrl, httpClient);
        }
        return mNotifySettings;
    }

    /**
     * If fullscreen is enabled and we are on at least android 4.4 set
     * the system visibility to fullscreen + immersive + noNav
     *
     * @author Dan Cunningham
     */
    protected void checkFullscreen() {
        if (isFullscreenEnabled()) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }

    /**
     * If we are 4.4 we can use fullscreen mode and Daydream features
     */
    protected boolean isFullscreenEnabled() {
        boolean supportsKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        boolean fullScreen = mSettings.getBoolean("default_openhab_fullscreen", false);
        return supportsKitKat && fullScreen;
    }


    private void loadDrawerItems() {
        mDrawerItemList.clear();
        if (mSitemapList != null) {
            mDrawerItemList.add(OpenHABDrawerItem.headerItem("Sitemaps"));
            for (OpenHABSitemap sitemap : mSitemapList) {
                mDrawerItemList.add(new OpenHABDrawerItem(sitemap));
            }
            mDrawerItemList.add(OpenHABDrawerItem.dividerItem());
        }
//        mDrawerItemList.add(OpenHABDrawerItem.menuItem("Favorites", getResources().getDrawable(R.drawable.ic_star_grey600_36dp)));
        // Only show Notifications item if using my.openHAB
        if (mIsMyOpenHAB)
//            mDrawerItemList.add(OpenHABDrawerItem.menuWithCountItem("Notifications", getResources().getDrawable(R.drawable.ic_notifications_grey600_36dp), 21));
            mDrawerItemList.add(OpenHABDrawerItem.menuItem("Notifications", getResources().getDrawable(R.drawable.ic_notifications_grey600_36dp), DRAWER_NOTIFICATIONS));
        // Only show those items if openHAB version is >= 2, openHAB 1.x just don't have those APIs...
        if (mOpenHABVersion >= 2) {
            mDrawerItemList.add(OpenHABDrawerItem.menuItem("Discovery", getResources().getDrawable(R.drawable.ic_track_changes_grey600_36dp), DRAWER_INBOX));
//            mDrawerItemList.add(OpenHABDrawerItem.menuWithCountItem("New devices", getResources().getDrawable(R.drawable.ic_inbox_grey600_36dp), 2, DRAWER_INBOX));
//            mDrawerItemList.add(OpenHABDrawerItem.menuItem("Things", getResources().getDrawable(R.drawable.ic_surround_sound_grey600_36dp)));
            mDrawerItemList.add(OpenHABDrawerItem.menuItem("Bindings", getResources().getDrawable(R.drawable.ic_extension_grey600_36dp), DRAWER_BINDINGS));
//        mDrawerItemList.add(OpenHABDrawerItem.menuItem("openHAB info", getResources().getDrawable(R.drawable.ic_info_grey600_36dp)));
//            mDrawerItemList.add(OpenHABDrawerItem.menuItem("Setup", getResources().getDrawable(R.drawable.ic_settings_grey600_36dp)));
        }
        mDrawerAdapter.notifyDataSetChanged();
    }
}

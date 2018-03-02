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
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.GcmIntentService;
import org.openhab.habdroid.core.NetworkConnectivityInfo;
import org.openhab.habdroid.core.NotificationDeletedBroadcastReceiver;
import org.openhab.habdroid.core.OnUpdateBroadcastReceiver;
import org.openhab.habdroid.core.OpenHABVoiceService;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionAvailabilityAwareActivity;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.DemoConnection;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.core.message.MessageHandler;
import org.openhab.habdroid.core.notifications.GoogleCloudMessageConnector;
import org.openhab.habdroid.core.notifications.NotificationSettings;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.ui.drawer.OpenHABDrawerAdapter;
import org.openhab.habdroid.ui.drawer.OpenHABDrawerItem;
import org.openhab.habdroid.util.AsyncServiceResolver;
import org.openhab.habdroid.util.AsyncServiceResolverListener;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.util.ArrayList;
import java.util.List;

import javax.jmdns.ServiceInfo;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import de.duenndns.ssl.MTMDecision;
import de.duenndns.ssl.MemorizingResponder;
import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.Call;
import okhttp3.Headers;

import static org.openhab.habdroid.core.message.MessageHandler.LOGLEVEL_ALWAYS;
import static org.openhab.habdroid.core.message.MessageHandler.LOGLEVEL_DEBUG;
import static org.openhab.habdroid.core.message.MessageHandler.LOGLEVEL_NO_DEBUG;
import static org.openhab.habdroid.core.message.MessageHandler.TYPE_DIALOG;
import static org.openhab.habdroid.core.message.MessageHandler.TYPE_SNACKBAR;
import static org.openhab.habdroid.util.Util.exceptionHasCause;
import static org.openhab.habdroid.util.Util.removeProtocolFromUrl;

public class OpenHABMainActivity extends ConnectionAvailabilityAwareActivity
        implements MemorizingResponder, AsyncServiceResolverListener {

    private abstract class DefaultHttpResponseHandler implements MyHttpClient.ResponseHandler {

        @Override
        public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
            setProgressIndicatorVisible(false);
            Log.e(TAG, "Error: " + error.toString());
            Log.e(TAG, "HTTP status code: " + statusCode);
            String message;
            if (statusCode >= 400){
                int resourceID;
                try {
                    resourceID = getResources().getIdentifier("error_http_code_" + statusCode, "string", getPackageName());
                    message = getString(resourceID);
                } catch (android.content.res.Resources.NotFoundException e) {
                    message = String.format(getString(R.string.error_http_connection_failed), statusCode);
                }
            } else if (error instanceof UnknownHostException) {
                Log.e(TAG, "Unable to resolve hostname");
                message = getString(R.string.error_unable_to_resolve_hostname);
            } else if (error instanceof SSLException) {
                // if ssl exception, check for some common problems
                if (exceptionHasCause(error, CertPathValidatorException.class)) {
                    message = getString(R.string.error_certificate_not_trusted);
                } else if (exceptionHasCause(error, CertificateExpiredException.class)) {
                    message = getString(R.string.error_certificate_expired);
                } else if (exceptionHasCause(error, CertificateNotYetValidException.class)) {
                    message = getString(R.string.error_certificate_not_valid_yet);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        && exceptionHasCause(error, CertificateRevokedException.class)) {
                    message = getString(R.string.error_certificate_revoked);
                } else if (exceptionHasCause(error, SSLPeerUnverifiedException.class)) {
                    message = String.format(getString(R.string.error_certificate_wrong_host),
                            removeProtocolFromUrl(call.request().url().toString()));
                } else {
                    message = getString(R.string.error_connection_sslhandshake_failed);
                }
            } else if (error instanceof ConnectException) {
                message = getString(R.string.error_connection_failed);
            } else {
                Log.e(TAG, error.getClass().toString());
                message = error.getMessage();
            }
            mMessageHandler.showMessageToUser(message, TYPE_DIALOG, LOGLEVEL_NO_DEBUG);

            message += "\nURL: " + call.request().url();
            if (call.request().header("Authorization") != null)
                message += "\n" + getUserPasswordFromAuthorization(call.request().header
                        ("Authorization"));
            message += "\nStacktrace:\n" + Log.getStackTraceString(error);
            mMessageHandler.showMessageToUser(message, TYPE_DIALOG, LOGLEVEL_DEBUG);
        }

        private String getUserPasswordFromAuthorization(String authorizationString) {
            if (authorizationString != null && authorizationString.startsWith("Basic")) {
                String base64Credentials = authorizationString.substring("Basic".length()).trim();
                String credentials = new String(Base64.decode(base64Credentials, Base64.DEFAULT),
                        Charset.forName("UTF-8"));
                final String[] values = credentials.split(":", 2);

                return "Username: " + values[0] + "\nPassword: " + values[1];
            }
            return "";
        }

    }
    // GCM Registration expiration
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;

    // Logging TAG
    private static final String TAG = OpenHABMainActivity.class.getSimpleName();
    // Activities request codes
    private static final int INTRO_REQUEST_CODE = 1001;
    private static final int SETTINGS_REQUEST_CODE = 1002;
    private static final int WRITE_NFC_TAG_REQUEST_CODE = 1003;
    private static final int INFO_REQUEST_CODE = 1004;
    // Drawer item codes
    private static final int DRAWER_NOTIFICATIONS = 100;
    private static final int DRAWER_ABOUT = 101;
    private static final int DRAWER_PREFERENCES = 102;
    private static final String EXTRA_DEMO_FIRST_TIME = "firstDemo";

    // openHAB Bonjour service name
    private String openHABServiceType;

    // view pager for widgetlist fragments
    private ViewPager pager;
    // view pager adapter for widgetlist fragments
    private OpenHABFragmentPagerAdapter pagerAdapter;
    // root URL of the current sitemap
    private String sitemapRootUrl;
    // A fragment which retains it's state through configuration changes to keep the current state of the app
    private StateRetainFragment stateFragment;
    // preferences
    private SharedPreferences mSettings;
    // Progress dialog
    private ProgressDialog mProgressDialog;
    private AsyncServiceResolver mServiceResolver;
    // NFC Launch data
    private String mNfcData;
    // Pending NFC page
    private String mPendingNfcPage;
    // Pending Notification page
    private Integer mNotificationPosition;
    // Toolbar / Actionbar
    private Toolbar mToolbar;
    // Drawer Layout
    private DrawerLayout mDrawerLayout;
    // Drawer Toggler
    private ActionBarDrawerToggle mDrawerToggle;
    // Google Cloud Messaging
    private GoogleCloudMessaging mGcm;
    private OpenHABDrawerAdapter mDrawerAdapter;
    private RecyclerView.RecycledViewPool mViewPool;
    private ArrayList<OpenHABSitemap> mSitemapList;
    private NetworkConnectivityInfo mStartedWithNetworkConnectivityInfo;
    private int mOpenHABVersion;
    private List<OpenHABDrawerItem> mDrawerItemList;
    private ProgressBar mProgressBar;
    private NotificationSettings mNotifySettings = null;
    // select sitemap dialog
    private Dialog selectSitemapDialog;
    private boolean mShowNetworkDrawerItems = true;
    public static String GCM_SENDER_ID;

    /**
     * Daydreaming gets us into a funk when in fullscreen, this allows us to
     * reset ourselves to fullscreen.
     * @author Dan Cunningham
     */
    private BroadcastReceiver dreamReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("INTENTFILTER", "Recieved intent: " + intent.toString());
            checkFullscreen();
        }
    };

    /**
     * This method is called when activity receives a new intent while running
     */
    @Override
    protected void onNewIntent(Intent intent) {
        processIntent(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");

        // Set default values, false means do it one time during the very first launch
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Set the theme to one from preferences
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        // Disable screen timeout if set in preferences
        if (mSettings.getBoolean(Constants.PREFERENCE_SCREENTIMEROFF, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Fetch openHAB service type name from strings.xml
        openHABServiceType = getString(R.string.openhab_service_type);

        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        setupToolbar();

        setupDrawer();
        gcmRegisterBackground();
        setupPager();

        mViewPool = new RecyclerView.RecycledViewPool();
        MemorizingTrustManager.setResponder(this);

        // Check if we have openHAB page url in saved instance state?
        if (savedInstanceState != null) {
            sitemapRootUrl = savedInstanceState.getString("sitemapRootUrl");
            mStartedWithNetworkConnectivityInfo = savedInstanceState.getParcelable("startedWithNetworkConnectivityInfo");
            mOpenHABVersion = savedInstanceState.getInt("openHABVersion");
            mSitemapList = savedInstanceState.getParcelableArrayList("sitemapList");
        }

        if (mSitemapList == null) {
            mSitemapList = new ArrayList<>();
        }

        if (getIntent() != null) {
            processIntent(getIntent());
        }

        if (isFullscreenEnabled()) {
            registerReceiver(dreamReceiver, new IntentFilter("android.intent.action.DREAMING_STARTED"));
            registerReceiver(dreamReceiver, new IntentFilter("android.intent.action.DREAMING_STOPPED"));
            checkFullscreen();
        }

        //  Create a new boolean and preference and set it to true
        boolean isFirstStart = mSettings.getBoolean("firstStart", true);

        SharedPreferences.Editor prefsEdit = sharedPrefs.edit();
        //  If the activity has never started before...
        if (isFirstStart) {

            //  Launch app intro
            final Intent i = new Intent(OpenHABMainActivity.this, IntroActivity.class);
            startActivityForResult(i, INTRO_REQUEST_CODE);

            prefsEdit.putBoolean("firstStart", false).apply();
        }
        OnUpdateBroadcastReceiver.updateComparableVersion(prefsEdit);
        prefsEdit.apply();
    }

    private void initializeConnectivity() throws ConnectionException {
        final Connection conn = ConnectionFactory.getUsableConnection();
        if (conn == null) {
            return;
        }
        if (conn instanceof DemoConnection) {
            mMessageHandler.showMessageToUser(
                    getString(R.string.info_demo_mode_short), TYPE_SNACKBAR, LOGLEVEL_ALWAYS);
            if (getIntent().hasExtra(EXTRA_DEMO_FIRST_TIME)) {
                getIntent().removeExtra(EXTRA_DEMO_FIRST_TIME);
                mMessageHandler.showMessageToUser(getString(R.string.error_no_url_start_demo_mode),
                        TYPE_DIALOG, LOGLEVEL_ALWAYS);
            }
        } else {
            boolean hasLocalAndRemote =
                    ConnectionFactory.getConnection(Connection.TYPE_LOCAL) != null &&
                    ConnectionFactory.getConnection(Connection.TYPE_REMOTE) != null;
            int type = conn.getConnectionType();
            @StringRes int noticeResId =
                    hasLocalAndRemote && type == Connection.TYPE_LOCAL ? R.string.info_conn_url :
                    hasLocalAndRemote && type == Connection.TYPE_REMOTE ? R.string.info_conn_rem_url :
                    0;
            if (noticeResId != 0) {
                mMessageHandler.showMessageToUser(getString(noticeResId),
                        TYPE_SNACKBAR, LOGLEVEL_ALWAYS);
            }
        }

        final String url = "/rest/bindings";
        conn.getAsyncHttpClient().get(url, new DefaultHttpResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                if (statusCode == 404) {
                    // no bindings endpoint; we're likely talking to an OH1 instance
                    mOpenHABVersion = 1;
                    conn.getAsyncHttpClient().addHeader("Accept", "application/xml");
                    selectSitemap();
                } else {
                    // other error -> use default handling
                    super.onFailure(call, statusCode, headers, responseBody, error);
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                mOpenHABVersion = 2;
                conn.getAsyncHttpClient().removeHeader("Accept");
                Log.d(TAG, "openHAB version 2");
                selectSitemap();
            }
        });
    }

    private void discoverOpenHAB() {
        if (mServiceResolver != null && mServiceResolver.isAlive()) {
            Log.d(TAG, "openHAB is already being discovered, another start of discovery refused.");
            return;
        }
        mProgressDialog = ProgressDialog.show(this, "",
                getString(R.string.info_discovery), true);

        mServiceResolver = new AsyncServiceResolver(
                this, this, openHABServiceType);
        mServiceResolver.start();
    }

    @Override
    public void onServiceResolved(ServiceInfo serviceInfo) {
        stopProgressDialog();

        Log.d(TAG, "Service resolved: "
                + serviceInfo.getHostAddresses()[0]
                + " port:" + serviceInfo.getPort());
        String openHABUrl = "http://" + serviceInfo.getHostAddresses()[0] + ":" +
                String.valueOf(serviceInfo.getPort()) + "/";

        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(Constants.PREFERENCE_LOCAL_URL, openHABUrl).apply();

        restartAfterSettingsUpdate();
    }

    @Override
    public void onServiceResolveFailed() {
        stopProgressDialog();
        PreferenceManager
                .getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(Constants.PREFERENCE_DEMOMODE, true)
                .apply();
        restartAfterSettingsUpdate(true);
    }

    private void stopProgressDialog() {
        if (!isFinishing() && mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    private void processIntent(Intent intent) {
        Log.d(TAG, "Intent != null");
        if (intent.getAction() != null) {
            Log.d(TAG, "Intent action = " + intent.getAction());
            if (intent.getAction().equals("android.nfc.action.NDEF_DISCOVERED")) {
                Log.d(TAG, "This is NFC action");
                if (intent.getDataString() != null) {
                    Log.d(TAG, "NFC data = " + intent.getDataString());
                    mNfcData = intent.getDataString();
                }
            } else if (intent.getAction().equals(GcmIntentService.ACTION_NOTIFICATION_SELECTED)) {
                onNotificationSelected(intent);
            } else if (intent.getAction().equals("android.intent.action.VIEW")) {
                Log.d(TAG, "This is URL Action");
                mNfcData = intent.getDataString();
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDrawerToggle != null) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    /**
     * Restore the fragment, which was saved in the onSaveInstanceState handler, if there's any.
     *
     * @param savedInstanceState
     */
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        int savedFragment = savedInstanceState.getInt("currentFragment", 0);
        if (savedFragment != 0) {
            pager.setCurrentItem(savedFragment);
            Log.d(TAG, String.format("Loaded current page = %d", savedFragment));
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        try {
            initializeConnectivity();
        } catch (NoUrlInformationException e) {
            NoUrlInformationException nuie = (NoUrlInformationException) e;
            if (nuie.wouldHaveUsedLocalConnection()) {
                Log.d(TAG, "No connection data available, start discovery.", nuie);
                discoverOpenHAB();
            } else {
                Log.d(TAG, "No remote connection available");
                onServiceResolveFailed();
            }
            return;
        } catch (ConnectionException e) {
            // will be handled by #getConnection if it is used later
        }

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
        Boolean startOver = stateFragment == null || stateFragment.getFragmentList().size() == 0;
        if (startOver || !NetworkConnectivityInfo.currentNetworkConnectivityInfo(this).equals(mStartedWithNetworkConnectivityInfo)) {
            resetStateFragmentAfterResume(fm);
        } else {
            // If connectivity type changed while we were in background
            // Restart the whole process
            if (!NetworkConnectivityInfo.currentNetworkConnectivityInfo(this).equals(mStartedWithNetworkConnectivityInfo)) {
                Log.d(TAG, "Connectivity type changed while I was out, or zero fragments found, need to restart");
                resetStateFragmentAfterResume(fm);
                // Clean up any existing fragments
                pagerAdapter.clearFragmentList();
                // Clean up title
                this.setTitle(R.string.app_name);
                return;
            }
            // If state fragment exists and contains something then just restore the fragments
            Log.d(TAG, "State fragment found");
            pagerAdapter.setFragmentList(stateFragment.getFragmentList());
            Log.d(TAG, String.format("Loaded %d fragments", stateFragment.getFragmentList().size()));
            pager.setCurrentItem(stateFragment.getCurrentPage());
        }
        if (!TextUtils.isEmpty(mPendingNfcPage)) {
            openPageIfPending(mPendingNfcPage);
            mPendingNfcPage = null;
        }

        if (mNotificationPosition != null) {
            openPageIfPending(mNotificationPosition);
            mNotificationPosition = null;
        }

        checkFullscreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mServiceResolver != null && mServiceResolver.isAlive()) {
            mServiceResolver.interrupt();
            mServiceResolver = null;
        }
    }

    /**
     * Resets the state of the app and activity after a fresh start or network change was
     * recognized. Helper method for onResume only.
     *
     * @param fm
     */
    private void resetStateFragmentAfterResume(FragmentManager fm) {
        stateFragment = new StateRetainFragment();
        fm.beginTransaction().add(stateFragment, "stateFragment").commit();
        mStartedWithNetworkConnectivityInfo = NetworkConnectivityInfo.currentNetworkConnectivityInfo(this);

        onConnectionChanged();
    }

    @Override
    protected void onEnterNoNetwork() {
        super.onEnterNoNetwork();
        ViewPager pager = findViewById(R.id.pager);
        if (pager != null) {
            pager.removeAllViews();
        }

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mShowNetworkDrawerItems = false;
        loadDrawerItems();

        mProgressBar.setVisibility(View.GONE);
        invalidateOptionsMenu();
    }

    @Override
    protected void onLeaveNoNetwork() {
        super.onLeaveNoNetwork();
        mShowNetworkDrawerItems = true;
        mDrawerToggle.setDrawerIndicatorEnabled(pager.getCurrentItem() == 0);
        loadDrawerItems();

        invalidateOptionsMenu();
    }

    @Override
    public void onConnectionChanged() {
        super.onConnectionChanged();

        try {
            initializeConnectivity();
        } catch (ConnectionException e) {
            // will be handled by #getConnection() later
        }

        mViewPool.clear();
        initDrawerAdapter();
        setupPager();
        selectSitemap();

        if (!TextUtils.isEmpty(mNfcData)) {
            onNfcTag(mNfcData);
            openPageIfPending(mPendingNfcPage);
        }
    }

    /**
     * Overriding onStop to enable Google Analytics stats collection
     */
    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        if(selectSitemapDialog != null && selectSitemapDialog.isShowing()) {
            selectSitemapDialog.dismiss();
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
                if (mSitemapList == null)
                    return;

                loadSitemapList();
                super.onDrawerOpened(drawerView);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mDrawerItemList = new ArrayList<>();
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int item, long l) {
                Log.d(TAG, "Drawer selected item " + String.valueOf(item));
                if (mDrawerItemList != null && mDrawerItemList.get(item).getItemType() == OpenHABDrawerItem.DrawerItemType.SITEMAP_ITEM) {
                    Log.d(TAG, "This is sitemap " + mDrawerItemList.get(item).getSiteMap().getLink());
                    mDrawerLayout.closeDrawers();
                    openSitemap(mDrawerItemList.get(item).getSiteMap());
                } else {
                    Log.d(TAG, "This is not sitemap");
                    if (mDrawerItemList.get(item).getTag() == DRAWER_NOTIFICATIONS) {
                        Log.d(TAG, "Notifications selected");
                        mDrawerLayout.closeDrawers();
                        OpenHABMainActivity.this.openNotifications();
                    } else if (mDrawerItemList.get(item).getTag() == DRAWER_PREFERENCES) {
                        Intent settingsIntent = new Intent(OpenHABMainActivity.this, OpenHABPreferencesActivity.class);
                        startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE);
                        Util.overridePendingTransition(OpenHABMainActivity.this, false);
                    } else if (mDrawerItemList.get(item).getTag() == DRAWER_ABOUT) {
                        OpenHABMainActivity.this.openAbout();
                    }
                }
            }
        });
        initDrawerAdapter();
    }

    private void initDrawerAdapter() {
        final ListView drawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerAdapter = new OpenHABDrawerAdapter(this, R.layout.openhabdrawer_sitemap_item,
                mDrawerItemList, getConnection());
        drawerList.setAdapter(mDrawerAdapter);
        loadDrawerItems();
    }

    private void openAbout() {
        Intent aboutIntent = new Intent(this, AboutActivity.class);
        aboutIntent.putExtra("openHABVersion", mOpenHABVersion);

        startActivityForResult(aboutIntent, INFO_REQUEST_CODE);
        Util.overridePendingTransition(this, false);
    }

    private void setupPager() {
        pagerAdapter = new OpenHABFragmentPagerAdapter(getSupportFragmentManager());
        pagerAdapter.setColumnsNumber(getResources().getInteger(R.integer.pager_columns));
        pager = findViewById(R.id.pager);
        pager.setAdapter(pagerAdapter);
        pager.addOnPageChangeListener(pagerAdapter);
    }

    public void openPageIfPending(int pagePosition) {
        pager.setCurrentItem(pagePosition);
    }

    public void openPageIfPending(String pendingPage) {
        int possiblePosition = pagerAdapter.getPositionByUrl(pendingPage);
        // If yes, then just switch to this page
        if (possiblePosition >= 0) {
            openPageIfPending(possiblePosition);
            // If not, then open this page as new one
        } else {
            pagerAdapter.openPage(pendingPage, null);
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
    }

    private void loadSitemapList() {
        Connection conn = getConnection();
        if (conn == null) {
            return;
        }
        Log.d(TAG, "Loading sitemap list from /rest/sitemaps");

        setProgressIndicatorVisible(true);
        conn.getAsyncHttpClient().get("/rest/sitemaps", new DefaultHttpResponseHandler() {
            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
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
                        Log.e(TAG, e.getMessage(), e);
                    }
                    // Later versions work with JSON
                } else {
                    try {
                        String jsonString = new String(responseBody, "UTF-8");
                        JSONArray jsonArray = new JSONArray(jsonString);
                        mSitemapList.addAll(Util.parseSitemapList(jsonArray));
                        Log.d(TAG, jsonArray.toString());
                    } catch (UnsupportedEncodingException | JSONException e) {
                        Log.e(TAG, e.getMessage(), e);
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
     */

    private void selectSitemap() {
        Connection conn = getConnection();
        if (conn == null) {
            return;
        }
        Log.d(TAG, "Loading sitemap list from /rest/sitemaps");

        setProgressIndicatorVisible(true);
        conn.getAsyncHttpClient().get("/rest/sitemaps", new DefaultHttpResponseHandler() {
            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
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
                        Log.e(TAG, e.getMessage(), e);
                    }
                    // Later versions work with JSON
                } else {
                    try {
                        String jsonString = new String(responseBody, "UTF-8");
                        JSONArray jsonArray = new JSONArray(jsonString);
                        mSitemapList.addAll(Util.parseSitemapList(jsonArray));
                        Log.d(TAG, jsonArray.toString());
                    } catch (UnsupportedEncodingException | JSONException e) {
                        Log.e(TAG, e.getMessage(), e);
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

                // Check if we have a sitemap configured to use
                SharedPreferences settings =
                        PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
                String configuredSitemap = settings.getString(Constants.PREFERENCE_SITEMAP_NAME, "");
                // If we have sitemap configured
                if (configuredSitemap.length() > 0) {
                    // Configured sitemap is on the list we got, open it!
                    if (Util.sitemapExists(mSitemapList, configuredSitemap)) {
                        Log.d(TAG, "Configured sitemap is on the list");
                        OpenHABSitemap selectedSitemap = Util.getSitemapByName(mSitemapList,
                                configuredSitemap);
                        openSitemap(selectedSitemap);
                        // Configured sitemap is not on the list we got!
                    } else {
                        Log.d(TAG, "Configured sitemap is not on the list");
                        if (mSitemapList.size() == 1) {
                            Log.d(TAG, "Got only one sitemap");
                            SharedPreferences.Editor preferencesEditor = settings.edit();
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP_NAME,
                                    mSitemapList.get(0).getName());
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP_LABEL,
                                    mSitemapList.get(0).getLabel());
                            preferencesEditor.apply();
                            openSitemap(mSitemapList.get(0));
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
                        preferencesEditor.putString(Constants.PREFERENCE_SITEMAP_NAME,
                                mSitemapList.get(0).getName());
                        preferencesEditor.putString(Constants.PREFERENCE_SITEMAP_LABEL,
                                mSitemapList.get(0).getLabel());
                        preferencesEditor.apply();
                        openSitemap(mSitemapList.get(0));
                    } else {
                        Log.d(TAG, "Got multiply sitemaps, user have to select one");
                        showSitemapSelectionDialog(mSitemapList);
                    }
                }
            }
        });
    }

    private void showSitemapSelectionDialog(final List<OpenHABSitemap> sitemapList) {
        Log.d(TAG, "Opening sitemap selection dialog");
        if (selectSitemapDialog != null && selectSitemapDialog.isShowing()) {
            return;
        }
        final List<String> sitemapLabelList = new ArrayList<String>();
        for (int i = 0; i < sitemapList.size(); i++) {
            sitemapLabelList.add(sitemapList.get(i).getLabel());
        }
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(OpenHABMainActivity.this);
        dialogBuilder.setTitle(getString(R.string.mainmenu_openhab_selectsitemap));
        try {
            selectSitemapDialog = dialogBuilder.setItems(sitemapLabelList.toArray(new CharSequence[sitemapLabelList.size()]),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            Log.d(TAG, "Selected sitemap " + sitemapList.get(item).getName());
                            SharedPreferences settings =
                                    PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
                            SharedPreferences.Editor preferencesEditor = settings.edit();
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP_NAME, sitemapList.get(item).getName());
                            preferencesEditor.putString(Constants.PREFERENCE_SITEMAP_LABEL, sitemapList.get(item).getLabel());
                            preferencesEditor.apply();
                            openSitemap(sitemapList.get(item));
                        }
                    }).show();
        } catch (WindowManager.BadTokenException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public void openNotifications() {
        if (this.pagerAdapter != null) {
            pagerAdapter.openNotifications();
            pager.setCurrentItem(pagerAdapter.getCount() - 1);
        }
        mDrawerToggle.setDrawerIndicatorEnabled(false);
    }

    private void openSitemap(OpenHABSitemap sitemap) {
        Log.i(TAG, "Opening sitemap at " + sitemap.getHomepageLink());
        sitemapRootUrl = sitemap.getHomepageLink();
        pagerAdapter.clearFragmentList();
        pagerAdapter.openPage(sitemap.getHomepageLink(), sitemap.getLabel());
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
        Connection c = null;
        try {
            c = ConnectionFactory.getUsableConnection();
        } catch (ConnectionException e) {
            // keep c at null
        }

        MenuItem voiceRecognitionItem = menu.findItem(R.id.mainmenu_voice_recognition);
        voiceRecognitionItem.setVisible(
                c != null && SpeechRecognizer.isRecognitionAvailable(this));
        voiceRecognitionItem.getIcon()
                .setColorFilter(ContextCompat.getColor(this, R.color.light), PorterDuff.Mode.SRC_IN);

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
                restartAfterSettingsUpdate();
                break;
            case INTRO_REQUEST_CODE:
                break;
            case WRITE_NFC_TAG_REQUEST_CODE:
                Log.d(TAG, "Got back from Write NFC tag");
                break;
            default:
        }
    }

    private void restartAfterSettingsUpdate() {
        restartAfterSettingsUpdate(false);
    }

    private void restartAfterSettingsUpdate(boolean firstTimeDemo) {
        // Restart app after preferences
        Log.d(TAG, "Restarting after settings");
        // Get launch intent for application
        Intent restartIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (firstTimeDemo) {
            restartIntent.putExtra(EXTRA_DEMO_FIRST_TIME, true);
        }
        if (firstTimeDemo) {
            // Finish current activity
            finish();
            // Start launch activity
            startActivity(restartIntent);
        }
        recreate();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onSaveInstanceState");
        if (pagerAdapter == null || stateFragment == null) {
            return;
        }
        // Save opened framents into state retaining fragment (I love Google! :-)
        Log.d(TAG, String.format("Saving %d fragments", pagerAdapter.getFragmentList().size()));
        Log.d(TAG, String.format("Saving current page = %d", pager.getCurrentItem()));
        stateFragment.setFragmentList(pagerAdapter.getFragmentList());
        stateFragment.setCurrentPage(pager.getCurrentItem());
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        savedInstanceState.putString("sitemapRootUrl", sitemapRootUrl);
        savedInstanceState.putInt("currentFragment", pager.getCurrentItem());
        savedInstanceState.putParcelable("startedWithNetworkConnectivityInfo", mStartedWithNetworkConnectivityInfo);
        savedInstanceState.putInt("openHABVersion", mOpenHABVersion);
        savedInstanceState.putParcelableArrayList("sitemapList", mSitemapList);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void onNotificationSelected(Intent intent) {
        Log.d(TAG, "Notification was selected");
        if (intent.hasExtra(GcmIntentService.EXTRA_NOTIFICATION_ID)) {
            Log.d(TAG, String.format("Notification id = %d",
                    intent.getExtras().getInt(GcmIntentService.EXTRA_NOTIFICATION_ID)));
            // Make a fake broadcast intent to hide intent on other devices
            Intent deleteIntent = new Intent(this, NotificationDeletedBroadcastReceiver.class);
            deleteIntent.setAction(GcmIntentService.ACTION_NOTIFICATION_DELETED);
            deleteIntent.putExtra(GcmIntentService.EXTRA_NOTIFICATION_ID, intent.getExtras().getInt(GcmIntentService.EXTRA_NOTIFICATION_ID));
            sendBroadcast(deleteIntent);
        }

        if (getNotificationSettings() != null) {
            openNotifications();
            mNotificationPosition = pagerAdapter.getCount() - 1;
        }

        if (intent.hasExtra(GcmIntentService.EXTRA_MSG)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.dlg_notification_title));
            builder.setMessage(intent.getExtras().getString(GcmIntentService.EXTRA_MSG));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            AlertDialog dialog = builder.create();
            dialog.show();

        }
    }

    /**
     * This method processes new intents generated by NFC subsystem
     *
     * @param nfcData - a data which NFC subsystem got from the NFC tag
     */
    private void onNfcTag(String nfcData) {
        Connection c = getConnection();
        if (c == null) {
            return;
        }

        Log.d(TAG, "onNfcTag()");
        Uri openHABURI = Uri.parse(nfcData);
        Log.d(TAG, "NFC Scheme = " + openHABURI.getScheme());
        Log.d(TAG, "NFC Host = " + openHABURI.getHost());
        Log.d(TAG, "NFC Path = " + openHABURI.getPath());
        String nfcItem = openHABURI.getQueryParameter("item");
        String nfcCommand = openHABURI.getQueryParameter("command");
        // If there is no item parameter it means tag contains only sitemap page url
        if (TextUtils.isEmpty(nfcItem)) {
            Log.d(TAG, "This is a sitemap tag without parameters");
            // Form the new sitemap page url
            // Check if we have this page in stack?
            mPendingNfcPage = c.getOpenHABUrl() + "rest/sitemaps" + openHABURI.getPath();
        } else {
            Log.d(TAG, "Target item = " + nfcItem);
            String url = c.getOpenHABUrl() + "rest/items/" + nfcItem;
            Util.sendItemCommand(c.getAsyncHttpClient(), url, nfcCommand);
            // if mNfcData is not empty, this means we were launched with NFC touch
            // and thus need to autoexit after an item action
            if (!TextUtils.isEmpty(mNfcData))
                finish();
        }
        mNfcData = "";
    }

    public void onWidgetSelected(OpenHABLinkedPage linkedPage, OpenHABWidgetListFragment source) {
        Log.i(TAG, "Got widget link = " + linkedPage.getLink());
        Log.i(TAG, String.format("Link came from fragment on position %d", source.getPosition()));
        pagerAdapter.openPage(linkedPage, source.getPosition() + 1);
        pager.setCurrentItem(pagerAdapter.getCount() - 1);
        updateTitle();
        //set the drawer icon to a back arrow when not on the rook menu
        mDrawerToggle.setDrawerIndicatorEnabled(pager.getCurrentItem() == 0);
    }

    public void updateTitle() {
        int indexToUse = Math.max(0, pager.getCurrentItem() + 1 - pagerAdapter.getActualColumnsNumber());
        CharSequence title = pagerAdapter.getPageTitle(indexToUse);
        Log.d(TAG, "updateTitle: current " + pager.getCurrentItem() + " shown "
                + pagerAdapter.getActualColumnsNumber() + " index " + indexToUse + " -> title " + title);
        setTitle(title);
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
            updateTitle();
            //set the drawer icon back to to hamburger menu if on the root menu
            mDrawerToggle.setDrawerIndicatorEnabled(pager.getCurrentItem() == 0);
        }
    }

    public RecyclerView.RecycledViewPool getViewPool() {
        return mViewPool;
    }

    public MessageHandler getMessageHandler() {
        return mMessageHandler;
    }

    protected void setProgressIndicatorVisible(boolean visible) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void launchVoiceRecognition() {
        Intent callbackIntent = new Intent(this, OpenHABVoiceService.class);
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
            // todo url doesnt seem to work anymore
            // not sure, if this is called
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://market.android.com/details?id=com.google.android.voicesearch"));
            startActivity(browserIntent);
        }
    }

    private void showAlertDialog(String alertMessage) {
        if (!isFinishing()) {
            mMessageHandler.showMessageToUser(alertMessage,
                    MessageHandler.TYPE_DIALOG, MessageHandler.LOGLEVEL_ALWAYS);
        }
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

    public void makeDecision(int decisionId, String certMessage) {
        Log.d(TAG, String.format("MTM is asking for decision on id = %d", decisionId));
        if (mSettings.getBoolean(Constants.PREFERENCE_SSLCERT, false))
            MemorizingTrustManager.interactResult(decisionId, MTMDecision.DECISION_ONCE);
        else
            showCertificateDialog(decisionId, certMessage);
    }

    public int getOpenHABVersion() {
        return this.mOpenHABVersion;
    }

    public void gcmRegisterBackground() {
        OpenHABMainActivity.GCM_SENDER_ID = null;
        // if no notification settings can be constructed, no GCM registration can be made.
        if (getNotificationSettings() == null)
            return;

        if (mGcm == null)
            mGcm = GoogleCloudMessaging.getInstance(this);

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
     * @return Returns the NotificationSettings or null, if openHAB-cloud isn't used
     */
    public NotificationSettings getNotificationSettings() {
        if (mNotifySettings == null) {
            // We need settings
            if (mSettings == null)
                return null;

            Connection conn = ConnectionFactory.getConnection(Connection.TYPE_CLOUD);
            if (conn == null) {
                Log.d(TAG, "Remote URL, username or password are empty, no GCM registration will be made");
                return null;
            }

            try {
                new URL(conn.getOpenHABUrl());
            } catch(MalformedURLException ex) {
                Log.d(TAG, "Could not parse the baseURL to an URL: " + ex.getMessage());
                return null;
            }

            mNotifySettings = new NotificationSettings(conn);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
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
        if (mShowNetworkDrawerItems && mSitemapList != null) {
            mDrawerItemList.add(OpenHABDrawerItem.headerItem(getString(R.string.mainmenu_openhab_sitemaps)));
            for (OpenHABSitemap sitemap : mSitemapList) {
                mDrawerItemList.add(new OpenHABDrawerItem(sitemap));
            }
            mDrawerItemList.add(OpenHABDrawerItem.dividerItem());
        }
        int iconColor = ContextCompat.getColor(this, R.color.colorAccent_themeDark);
        Drawable notificationDrawable = getResources().getDrawable(R.drawable
                .ic_notifications_black_24dp);
        notificationDrawable.setColorFilter(
                iconColor,
                PorterDuff.Mode.SRC_IN
        );
        if (mShowNetworkDrawerItems && getNotificationSettings() != null) {
            mDrawerItemList.add(OpenHABDrawerItem.menuItem(
                    getString(R.string.app_notifications),
                    notificationDrawable,
                    DRAWER_NOTIFICATIONS
            ));
        }

        Drawable settingsDrawable = getResources().getDrawable(R.drawable
                .ic_settings_black_24dp);
        settingsDrawable.setColorFilter(
                iconColor,
                PorterDuff.Mode.SRC_IN
        );
        mDrawerItemList.add(OpenHABDrawerItem.menuItem(
                getString(R.string.mainmenu_openhab_preferences),
                settingsDrawable,
                DRAWER_PREFERENCES
        ));

        Drawable aboutDrawable = getResources().getDrawable(R.drawable.ic_info_outline);
        aboutDrawable.setColorFilter(
                iconColor,
                PorterDuff.Mode.SRC_IN);
        mDrawerItemList.add(OpenHABDrawerItem.menuItem(
                getString(R.string.about_title),
                aboutDrawable,
                DRAWER_ABOUT
        ));

        if (mDrawerAdapter != null) {
            mDrawerAdapter.notifyDataSetChanged();
        }
    }
}

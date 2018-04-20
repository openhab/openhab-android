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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.loopj.android.image.WebImageCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.core.OnUpdateBroadcastReceiver;
import org.openhab.habdroid.core.OpenHABVoiceService;
import org.openhab.habdroid.core.connection.CloudConnection;
import org.openhab.habdroid.core.connection.Connection;
import org.openhab.habdroid.core.connection.ConnectionFactory;
import org.openhab.habdroid.core.connection.DemoConnection;
import org.openhab.habdroid.core.connection.exception.ConnectionException;
import org.openhab.habdroid.core.connection.exception.NetworkNotSupportedException;
import org.openhab.habdroid.core.connection.exception.NoUrlInformationException;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.ui.activity.ContentController;
import org.openhab.habdroid.util.AsyncServiceResolver;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyHttpClient;
import org.openhab.habdroid.util.MyWebImage;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

import static org.openhab.habdroid.util.Util.exceptionHasCause;
import static org.openhab.habdroid.util.Util.removeProtocolFromUrl;

public class OpenHABMainActivity extends AppCompatActivity implements
        MemorizingResponder, AsyncServiceResolver.Listener, ConnectionFactory.UpdateListener {
    public static final String ACTION_NOTIFICATION_SELECTED =
            "org.openhab.habdroid.action.NOTIFICATION_SELECTED";
    public static final String EXTRA_MESSAGE = "message";

    private abstract class DefaultHttpResponseHandler implements MyHttpClient.ResponseHandler {

        @Override
        public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
            Log.e(TAG, "Error: " + error.toString());
            Log.e(TAG, "HTTP status code: " + statusCode);
            CharSequence message;
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
            } else if (error instanceof ConnectException || error instanceof SocketTimeoutException) {
                message = getString(R.string.error_connection_failed);
            } else {
                Log.e(TAG, "REST call to " + call.request().url() + " failed", error);
                message = error.getMessage();
            }

            SharedPreferences settings =
                    PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
            if (settings.getBoolean(Constants.PREFERENCE_DEBUG_MESSAGES, false)) {
                SpannableStringBuilder builder = new SpannableStringBuilder(message);
                int detailsStart = builder.length();

                builder.append("\n\nURL: ").append(call.request().url().toString());

                String authHeader = call.request().header("Authorization");
                if (authHeader != null && authHeader.startsWith("Basic")) {
                    String base64Credentials = authHeader.substring("Basic".length()).trim();
                    String credentials = new String(Base64.decode(base64Credentials, Base64.DEFAULT),
                            Charset.forName("UTF-8"));
                    String[] usernameAndPassword = credentials.split(":", 2);
                    builder.append("\nUsername: ").append(usernameAndPassword[0]);
                    builder.append("\nPassword: ").append(usernameAndPassword[1]);
                }

                builder.append("\nException stack:\n");

                int exceptionStart = builder.length();
                Throwable cause = error;
                do {
                    builder.append(cause.toString()).append('\n');
                    error = cause;
                    cause = error.getCause();
                } while (cause != null && error != cause);

                builder.setSpan(new RelativeSizeSpan(0.8f), detailsStart, exceptionStart,
                        SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(0.6f), exceptionStart, builder.length(),
                        SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE);
                message = builder;
            }

            mController.indicateServerCommunicationFailure(message);
            mPendingCall = null;
            mInitState = InitState.DONE;
        }
    }
    // Logging TAG
    private static final String TAG = OpenHABMainActivity.class.getSimpleName();
    // Activities request codes
    private static final int INTRO_REQUEST_CODE = 1001;
    private static final int SETTINGS_REQUEST_CODE = 1002;
    private static final int WRITE_NFC_TAG_REQUEST_CODE = 1003;
    private static final int INFO_REQUEST_CODE = 1004;
    // Drawer item codes
    private static final int GROUP_ID_SITEMAPS = 1;

    private enum InitState {
        QUERY_SERVER_PROPS,
        LOAD_SITEMAPS,
        DONE
    }

    // preferences
    private SharedPreferences mSettings;
    private AsyncServiceResolver mServiceResolver;
    // Toolbar / Actionbar
    private Toolbar mToolbar;
    // Drawer Layout
    private DrawerLayout mDrawerLayout;
    // Drawer Toggler
    private ActionBarDrawerToggle mDrawerToggle;
    private Menu mDrawerMenu;
    private ColorStateList mDrawerIconTintList;
    private RecyclerView.RecycledViewPool mViewPool;
    private ArrayList<OpenHABSitemap> mSitemapList;
    private int mOpenHABVersion;
    private ProgressBar mProgressBar;
    // select sitemap dialog
    private Dialog selectSitemapDialog;
    private Snackbar mLastSnackbar;
    private Connection mConnection;

    private Uri mPendingNfcData;
    private boolean mPendingOpenNotifications;
    private OpenHABSitemap mSelectedSitemap;
    private ContentController mController;
    private InitState mInitState = InitState.QUERY_SERVER_PROPS;
    private Call mPendingCall;

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

        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        String controllerClassName = getResources().getString(R.string.controller_class);
        try {
            Class<?> controllerClass = Class.forName(controllerClassName);
            Constructor<?> constructor = controllerClass.getConstructor(OpenHABMainActivity.class);
            mController = (ContentController) constructor.newInstance(this);
        } catch (Exception e) {
            Log.wtf(TAG, "Could not instantiate activity controller class '"
                    + controllerClassName + "'");
            throw new RuntimeException(e);
        }

        setContentView(R.layout.activity_main);
        // inflate the controller dependent content view
        ViewStub contentStub = findViewById(R.id.content_stub);
        mController.inflateViews(contentStub);

        setupToolbar();
        setupDrawer();

        mViewPool = new RecyclerView.RecycledViewPool();
        MemorizingTrustManager.setResponder(this);

        // Check if we have openHAB page url in saved instance state?
        if (savedInstanceState != null) {
            mOpenHABVersion = savedInstanceState.getInt("openHABVersion");
            mSitemapList = savedInstanceState.getParcelableArrayList("sitemapList");
            mSelectedSitemap = savedInstanceState.getParcelable("sitemap");
            mInitState = InitState.values()[savedInstanceState.getInt("initState")];
            int lastConnectionHash = savedInstanceState.getInt("connectionHash");
            if (lastConnectionHash != -1) {
                try {
                    Connection c = ConnectionFactory.getUsableConnection();
                    if (c != null && c.hashCode() == lastConnectionHash) {
                        mConnection = c;
                    }
                } catch (ConnectionException e) {
                    // ignored
                }
            }

            mController.onRestoreInstanceState(savedInstanceState);
            String lastControllerClass = savedInstanceState.getString("controller");
            if (!mController.getClass().getCanonicalName().equals(lastControllerClass)) {
                // Our controller type changed, so we need to make the new controller aware of the
                // page hierarchy. If the controller didn't change, the hierarchy will be restored
                // via the fragment state restoration.
                mController.recreateFragmentState();
            }
            updateSitemapDrawerItems();
        } else {
            mSitemapList = new ArrayList<>();
        }

        processIntent(getIntent());

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

    private void handleConnectionChange() {
        if (mConnection instanceof DemoConnection) {
            showSnackbar(R.string.info_demo_mode_short);
        } else {
            boolean hasLocalAndRemote =
                    ConnectionFactory.getConnection(Connection.TYPE_LOCAL) != null &&
                    ConnectionFactory.getConnection(Connection.TYPE_REMOTE) != null;
            int type = mConnection.getConnectionType();
            if (hasLocalAndRemote && type == Connection.TYPE_LOCAL) {
                showSnackbar(R.string.info_conn_url);
            } else if (hasLocalAndRemote && type == Connection.TYPE_REMOTE) {
                showSnackbar(R.string.info_conn_rem_url);
            }
        }
        queryServerProperties();
    }

    public void retryServerPropertyQuery() {
        mController.clearServerCommunicationFailure();
        if (mPendingCall != null) {
            mPendingCall.cancel();
        }
        queryServerProperties();
    }

    private void queryServerProperties() {
        final String url = "/rest/bindings";
        mInitState = InitState.QUERY_SERVER_PROPS;
        mPendingCall = mConnection.getAsyncHttpClient().get(url, new DefaultHttpResponseHandler() {
            @Override
            public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                if (statusCode == 404 && mConnection != null) {
                    // no bindings endpoint; we're likely talking to an OH1 instance
                    mOpenHABVersion = 1;
                    mConnection.getAsyncHttpClient().addHeader("Accept", "application/xml");
                    loadSitemapList(true);
                } else {
                    // other error -> use default handling
                    super.onFailure(call, statusCode, headers, responseBody, error);
                    mInitState = InitState.DONE;
                    mPendingCall = null;
                }
            }

            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                mOpenHABVersion = 2;
                mConnection.getAsyncHttpClient().removeHeader("Accept");
                Log.d(TAG, "openHAB version 2");
                loadSitemapList(true);
            }
        });
    }

    @Override
    public void onServiceResolved(ServiceInfo serviceInfo) {
        Log.d(TAG, "Service resolved: "
                + serviceInfo.getHostAddresses()[0]
                + " port:" + serviceInfo.getPort());
        String openHABUrl = "http://" + serviceInfo.getHostAddresses()[0] + ":" +
                String.valueOf(serviceInfo.getPort()) + "/";

        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(Constants.PREFERENCE_LOCAL_URL, openHABUrl).apply();
        // We'll get a connection update later
        mServiceResolver = null;
    }

    @Override
    public void onServiceResolveFailed() {
        mController.indicateMissingConfiguration();
        mServiceResolver = null;
    }

    private void processIntent(Intent intent) {
        Log.d(TAG, "Got intent: " + intent);
        String action = intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case NfcAdapter.ACTION_NDEF_DISCOVERED:
            case Intent.ACTION_VIEW:
                onNfcTag(intent.getData());
                break;
            case ACTION_NOTIFICATION_SELECTED:
                CloudMessagingHelper.onNotificationSelected(this, intent);
                onNotificationSelected(intent);
                break;
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

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");

        super.onResume();
        ConnectionFactory.addListener(this);

        onAvailableConnectionChanged();
        updateNotificationDrawerItem();

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            Intent intent = new Intent(this, getClass())
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
            nfcAdapter.enableForegroundDispatch(this, pi, null, null);
        }

        updateTitle();
        checkFullscreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mServiceResolver != null && mServiceResolver.isAlive()) {
            mServiceResolver.interrupt();
            mServiceResolver = null;
        }
        ConnectionFactory.removeListener(this);
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onAvailableConnectionChanged() {
        Connection newConnection;
        ConnectionException failureReason;

        try {
            newConnection = ConnectionFactory.getUsableConnection();
            failureReason = null;
        } catch (ConnectionException e) {
            newConnection = null;
            failureReason = e;
        }

        updateNotificationDrawerItem();

        if (newConnection != null && newConnection == mConnection) {
            return;
        }

        mConnection = newConnection;
        hideSnackbar();
        mSitemapList.clear();
        mSelectedSitemap = null;

        // Handle pending NFC tag if initial connection determination finished
        if (mPendingNfcData != null && (mConnection != null || failureReason != null)) {
            onNfcTag(mPendingNfcData);
            mPendingNfcData = null;
        }

        if (newConnection != null) {
            handleConnectionChange();
            mController.updateConnection(newConnection, null);
        } else {
            if (failureReason instanceof NoUrlInformationException) {
                NoUrlInformationException nuie = (NoUrlInformationException) failureReason;
                // Attempt resolving only if we're connected locally and
                // no local connection is configured yes
                if (nuie.wouldHaveUsedLocalConnection()
                        && ConnectionFactory.getConnection(Connection.TYPE_LOCAL) == null) {
                    if (mServiceResolver == null) {
                        mServiceResolver = new AsyncServiceResolver(this, this,
                                getString(R.string.openhab_service_type));
                        mServiceResolver.start();
                        mController.updateConnection(null, getString(R.string.resolving_openhab));
                    }
                } else {
                    mController.indicateMissingConfiguration();
                }
            } else if (failureReason != null) {
                final String message;
                if (failureReason instanceof NetworkNotSupportedException) {
                    NetworkInfo info = ((NetworkNotSupportedException) failureReason).getNetworkInfo();
                    message = getString(R.string.error_network_type_unsupported, info.getTypeName());
                } else {
                    message = getString(R.string.error_network_not_available);
                }
                mController.indicateNoNetwork(message);
            } else {
                mController.updateConnection(null, null);
            }
        }
        mViewPool.clear();
        updateSitemapDrawerItems();
        invalidateOptionsMenu();
        updateTitle();
    }

    @Override
    public void onCloudConnectionChanged(CloudConnection connection) {
        updateNotificationDrawerItem();
        if (mPendingOpenNotifications && connection != null) {
            openNotifications();
            mPendingOpenNotifications = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mConnection != null) {
            if (mInitState == InitState.QUERY_SERVER_PROPS) {
                mController.clearServerCommunicationFailure();
                queryServerProperties();
            } else if (mInitState == InitState.LOAD_SITEMAPS) {
                mController.clearServerCommunicationFailure();
                loadSitemapList(true);
            }
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
        if (mPendingCall != null) {
            mPendingCall.cancel();
        }
    }

    public void triggerPageUpdate(String pageUrl, boolean forceReload) {
        mController.triggerPageUpdate(pageUrl, forceReload);
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
        setProgressIndicatorVisible(false);
    }

    private void setupDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (mInitState == InitState.DONE) {
                    loadSitemapList(false);
                }
            }
        });
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        NavigationView drawerMenu = findViewById(R.id.left_drawer);
        drawerMenu.inflateMenu(R.menu.left_drawer);
        mDrawerMenu = drawerMenu.getMenu();

        // We only want to tint the menu icons, but not our loaded sitemap icons. NavigationView
        // unfortunately doesn't support this directly, so we tint the icon drawables manually
        // instead of letting NavigationView do it.
        mDrawerIconTintList = drawerMenu.getItemIconTintList();
        drawerMenu.setItemIconTintList(null);
        for (int i = 0; i < mDrawerMenu.size(); i++) {
            MenuItem item = mDrawerMenu.getItem(i);
            item.setIcon(applyDrawerIconTint(item.getIcon()));
        }

        drawerMenu.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                mDrawerLayout.closeDrawers();
                switch (item.getItemId()) {
                    case R.id.notifications:
                        openNotifications();
                        return true;
                    case R.id.settings:
                        Intent settingsIntent = new Intent(OpenHABMainActivity.this,
                                OpenHABPreferencesActivity.class);
                        startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE);
                        return true;
                    case R.id.about:
                        openAbout();
                        return true;
                }
                if (item.getGroupId() == GROUP_ID_SITEMAPS) {
                    OpenHABSitemap sitemap = mSitemapList.get(item.getItemId());
                    openSitemap(sitemap);
                    return true;
                }
                return false;
            }
        });
    }

    private void updateNotificationDrawerItem() {
        MenuItem notificationsItem = mDrawerMenu.findItem(R.id.notifications);
        notificationsItem.setVisible(ConnectionFactory.getConnection(Connection.TYPE_CLOUD) != null);
    }

    private void updateSitemapDrawerItems() {
        MenuItem sitemapItem = mDrawerMenu.findItem(R.id.sitemaps);

        if (mSitemapList.isEmpty()) {
            sitemapItem.setVisible(false);
        } else {
            sitemapItem.setVisible(true);
            SubMenu menu = sitemapItem.getSubMenu();
            menu.clear();

            for (int i = 0; i < mSitemapList.size(); i++) {
                OpenHABSitemap sitemap = mSitemapList.get(i);
                MenuItem item = menu.add(GROUP_ID_SITEMAPS, i, i, sitemap.label());
                loadSitemapIcon(sitemap, item);
            }
        }
    }

    private void loadSitemapIcon(final OpenHABSitemap sitemap, final MenuItem item) {
        final WebImageCache imageCache = MyWebImage.getWebImageCache(this);
        final String url = sitemap.icon() != null ? Uri.encode(sitemap.iconPath(), "/?=") : null;
        Bitmap cached = url != null ? imageCache.get(url) : null;

        if (cached != null) {
            item.setIcon(new BitmapDrawable(cached));
            return;
        }

        Drawable defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_openhab_appicon_24dp);
        item.setIcon(applyDrawerIconTint(defaultIcon));

        if (url != null) {
            mConnection.getAsyncHttpClient().get(url, new MyHttpClient.ResponseHandler() {
                @Override
                public void onFailure(Call call, int statusCode, Headers headers, byte[] responseBody, Throwable error) {
                    Log.w(TAG, "Could not fetch icon for sitemap " + sitemap.name());
                }
                @Override
                public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(responseBody, 0, responseBody.length);
                    if (bitmap != null) {
                        imageCache.put(url, bitmap);
                        item.setIcon(new BitmapDrawable(bitmap));
                    }
                }
            });
        }
    }

    private Drawable applyDrawerIconTint(Drawable icon) {
        if (icon == null) {
            return null;
        }
        Drawable wrapped = DrawableCompat.wrap(icon);
        DrawableCompat.setTintList(wrapped, mDrawerIconTintList);
        return wrapped;
    }

    private void openAbout() {
        Intent aboutIntent = new Intent(this, AboutActivity.class);
        aboutIntent.putExtra("openHABVersion", mOpenHABVersion);

        startActivityForResult(aboutIntent, INFO_REQUEST_CODE);
        Util.overridePendingTransition(this, false);
    }

    /**
     * Get sitemaps from openHAB, if user already configured preffered sitemap
     * just open it. If no preffered sitemap is configured - let user select one.
     */

    private void loadSitemapList(final boolean selectSitemapAfterLoad) {
        if (mConnection == null) {
            return;
        }

        Log.d(TAG, "Loading sitemap list from /rest/sitemaps");

        mInitState = InitState.LOAD_SITEMAPS;
        mPendingCall = mConnection.getAsyncHttpClient().get("/rest/sitemaps", new DefaultHttpResponseHandler() {
            @Override
            public void onSuccess(Call call, int statusCode, Headers headers, byte[] responseBody) {
                Log.d(TAG, new String(responseBody));
                mPendingCall = null;
                mInitState = InitState.DONE;

                // OH1 returns XML, later versions return JSON
                List<OpenHABSitemap> result = mOpenHABVersion == 1
                        ? loadSitemapsFromXml(responseBody)
                        : loadSitemapsFromJson(responseBody);
                Log.d(TAG, "Server returned sitemaps: " + result);
                mSitemapList.clear();
                if (result != null) {
                    String defaultSitemapName =
                            mSettings.getString(Constants.PREFERENCE_SITEMAP_NAME, "");
                    mSitemapList.addAll(Util.sortSitemapList(result, defaultSitemapName));
                }
                updateSitemapDrawerItems();

                if (!selectSitemapAfterLoad) {
                    return;
                }

                if (mSitemapList.isEmpty()) {
                    Log.e(TAG, "openHAB returned empty sitemap list");
                    mController.indicateServerCommunicationFailure(
                            getString(R.string.error_empty_sitemap_list));
                } else {
                    OpenHABSitemap sitemap = selectConfiguredSitemapFromList();
                    if (sitemap != null) {
                        openSitemap(sitemap);
                    } else {
                        showSitemapSelectionDialog();
                    }
                }
            }
        });
    }

    private static List<OpenHABSitemap> loadSitemapsFromXml(byte[] response) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document sitemapsXml = builder.parse(new ByteArrayInputStream(response));
            return Util.parseSitemapList(sitemapsXml);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e(TAG, "Failed parsing sitemap XML", e);
            return null;
        }
    }

    private static List<OpenHABSitemap> loadSitemapsFromJson(byte[] response) {
        try {
            String jsonString = new String(response, "UTF-8");
            JSONArray jsonArray = new JSONArray(jsonString);
            return Util.parseSitemapList(jsonArray);
        } catch (UnsupportedEncodingException | JSONException e) {
            Log.e(TAG, "Failed parsing sitemap JSON", e);
            return null;
        }
    }

    private OpenHABSitemap selectConfiguredSitemapFromList() {
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(this);
        String configuredSitemap = settings.getString(Constants.PREFERENCE_SITEMAP_NAME, "");
        final OpenHABSitemap result;

        if (mSitemapList.size() == 1) {
            // We only have one sitemap, use it
            result = mSitemapList.get(0);
        } else if (!configuredSitemap.isEmpty()) {
            // Select configured sitemap if still present, nothing otherwise
            result = Util.getSitemapByName(mSitemapList, configuredSitemap);
        } else {
            // Nothing configured -> can't auto-select anything
            result = null;
        }

        Log.d(TAG, "Configured sitemap is '" + configuredSitemap + "', selected " + result);
        boolean hasResult = result != null;
        boolean hasConfigured = !configuredSitemap.isEmpty();
        if (!hasResult && hasConfigured) {
            // clear old configuration
            settings.edit()
                    .remove(Constants.PREFERENCE_SITEMAP_LABEL)
                    .remove(Constants.PREFERENCE_SITEMAP_NAME)
                    .apply();
        } else if (hasResult && (!hasConfigured || !configuredSitemap.equals(result.name()))) {
            // update result
            settings.edit()
                    .putString(Constants.PREFERENCE_SITEMAP_NAME, result.name())
                    .putString(Constants.PREFERENCE_SITEMAP_LABEL, result.label())
                    .apply();
        }

        return result;
    }

    private void showSitemapSelectionDialog() {
        Log.d(TAG, "Opening sitemap selection dialog");
        if (selectSitemapDialog != null && selectSitemapDialog.isShowing()) {
            selectSitemapDialog.dismiss();
        }
        if (isFinishing()) {
            return;
        }

        final String[] sitemapLabels = new String[mSitemapList.size()];
        for (int i = 0; i < mSitemapList.size(); i++) {
            sitemapLabels[i] = mSitemapList.get(i).label();
        }
        selectSitemapDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.mainmenu_openhab_selectsitemap)
                .setItems(sitemapLabels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        OpenHABSitemap sitemap = mSitemapList.get(item);
                        Log.d(TAG, "Selected sitemap " + sitemap);
                        PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this)
                                .edit()
                                .putString(Constants.PREFERENCE_SITEMAP_NAME, sitemap.name())
                                .putString(Constants.PREFERENCE_SITEMAP_LABEL, sitemap.label())
                                .apply();
                        openSitemap(sitemap);
                    }
                })
                .show();
    }

    private void openNotifications() {
        mController.openNotifications();
        mDrawerToggle.setDrawerIndicatorEnabled(false);
    }

    private void openSitemap(OpenHABSitemap sitemap) {
        Log.i(TAG, "Opening sitemap " + sitemap + ", currently selected " + mSelectedSitemap);
        if (mSelectedSitemap != null && mSelectedSitemap.equals(sitemap)) {
            return;
        }
        mSelectedSitemap = sitemap;
        mController.openSitemap(sitemap);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem voiceRecognitionItem = menu.findItem(R.id.mainmenu_voice_recognition);
        voiceRecognitionItem.setVisible(
                mConnection != null && SpeechRecognizer.isRecognitionAvailable(this));
        voiceRecognitionItem.getIcon()
                .setColorFilter(ContextCompat.getColor(this, R.color.light), PorterDuff.Mode.SRC_IN);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        //clicking the back navigation arrow
        if (item.getItemId() == android.R.id.home && mController.canGoBack()) {
            mController.goBack();
            return true;
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
                if (data != null
                        && data.getBooleanExtra(OpenHABPreferencesActivity.RESULT_EXTRA_THEME_CHANGED, false)) {
                    recreate();
                }
                break;
            case INTRO_REQUEST_CODE:
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
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        savedInstanceState.putInt("openHABVersion", mOpenHABVersion);
        savedInstanceState.putParcelableArrayList("sitemapList", mSitemapList);
        savedInstanceState.putParcelable("sitemap", mSelectedSitemap);
        savedInstanceState.putString("controller", mController.getClass().getCanonicalName());
        savedInstanceState.putInt("connectionHash",
                mConnection != null ? mConnection.hashCode() : -1);
        savedInstanceState.putInt("initState", mInitState.ordinal());
        if (mPendingCall != null) {
            mPendingCall.cancel();
        }
        mController.onSaveInstanceState(savedInstanceState);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void onNotificationSelected(Intent intent) {
        Log.d(TAG, "Notification was selected");

        if (ConnectionFactory.getConnection(Connection.TYPE_CLOUD) != null) {
            openNotifications();
        } else {
            mPendingOpenNotifications = true;
        }

        if (intent.hasExtra(EXTRA_MESSAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dlg_notification_title))
                    .setMessage(intent.getStringExtra(EXTRA_MESSAGE))
                    .setPositiveButton(getString(android.R.string.ok), null)
                    .show();
        }
    }

    /**
     * This method processes new intents generated by NFC subsystem
     *
     * @param nfcData - a data which NFC subsystem got from the NFC tag
     */
    private void onNfcTag(Uri nfcData) {
        if (nfcData == null) {
            return;
        }
        if (mConnection == null) {
            mPendingNfcData = nfcData;
            return;
        }

        Log.d(TAG, "NFC Scheme = " + nfcData.getScheme());
        Log.d(TAG, "NFC Host = " + nfcData.getHost());
        Log.d(TAG, "NFC Path = " + nfcData.getPath());
        String nfcItem = nfcData.getQueryParameter("item");
        String nfcCommand = nfcData.getQueryParameter("command");

        // If there is no item parameter it means tag contains only sitemap page url
        if (TextUtils.isEmpty(nfcItem)) {
            Log.d(TAG, "This is a sitemap tag without parameters");
            // Form the new sitemap page url
            String newPageUrl = String.format(Locale.US, "%srest/sitemaps%s",
                    mConnection.getOpenHABUrl(), nfcData.getPath());
            mController.openPage(newPageUrl);
        } else {
            Log.d(TAG, "Target item = " + nfcItem);
            String url = String.format(Locale.US, "%srest/items/%s",
                    mConnection.getOpenHABUrl(), nfcItem);
            Util.sendItemCommand(mConnection.getAsyncHttpClient(), url, nfcCommand);
            finish();
        }
    }

    public void onWidgetSelected(OpenHABLinkedPage linkedPage, OpenHABWidgetListFragment source) {
        Log.i(TAG, "Got widget link = " + linkedPage.link());
        mController.openPage(linkedPage, source);
    }

    public void updateTitle() {
        CharSequence title = mController.getCurrentTitle();
        setTitle(title != null ? title : getString(R.string.app_name));
        mDrawerToggle.setDrawerIndicatorEnabled(!mController.canGoBack());
    }

    @Override
    public void onBackPressed() {
        if (mController.canGoBack()) {
            mController.goBack();
        } else if (!isFullscreenEnabled()) { //in fullscreen don't continue back which would exit the app
            super.onBackPressed();
        }
    }

    public RecyclerView.RecycledViewPool getViewPool() {
        return mViewPool;
    }

    public void setProgressIndicatorVisible(boolean visible) {
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

    public void showRefreshHintSnackbarIfNeeded() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED, false)) {
            return;
        }

        mLastSnackbar = Snackbar.make(findViewById(android.R.id.content),
                R.string.swipe_to_refresh_description, Snackbar.LENGTH_LONG);
        mLastSnackbar.setAction(R.string.swipe_to_refresh_dismiss, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit()
                        .putBoolean(Constants.PREFERENCE_SWIPE_REFRESH_EXPLAINED, true)
                        .apply();
            }
        });
        mLastSnackbar.show();
    }

    private void showSnackbar(@StringRes int messageResId) {
        mLastSnackbar = Snackbar.make(findViewById(android.R.id.content),
                messageResId, Snackbar.LENGTH_LONG);
        mLastSnackbar.show();
    }

    private void hideSnackbar() {
        if (mLastSnackbar != null) {
            mLastSnackbar.dismiss();
            mLastSnackbar = null;
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
        return mOpenHABVersion;
    }

    public Connection getConnection() {
        return mConnection;
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
}

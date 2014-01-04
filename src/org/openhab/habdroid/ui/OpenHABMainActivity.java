/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.crittercism.app.Crittercism;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.LocationClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.image.WebImageCache;

import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.StringEntity;
import org.openhab.habdroid.R;
import org.openhab.habdroid.core.DocumentHttpResponseHandler;
import org.openhab.habdroid.core.NotificationDeletedBroadcastReceiver;
import org.openhab.habdroid.core.OpenHABTracker;
import org.openhab.habdroid.core.OpenHABTrackerReceiver;
import org.openhab.habdroid.model.OpenHABLinkedPage;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.ui.drawer.OpenHABDrawerAdapter;
import org.openhab.habdroid.util.MyAsyncHttpClient;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import de.duenndns.ssl.MTMDecision;
import de.duenndns.ssl.MemorizingResponder;
import de.duenndns.ssl.MemorizingTrustManager;

public class OpenHABMainActivity extends FragmentActivity implements OnWidgetSelectedListener,
        OpenHABTrackerReceiver, MemorizingResponder {
    // Logging TAG
    private static final String TAG = "MainActivity";
    // Activities request codes
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;
    private static final int SETTINGS_REQUEST_CODE = 1002;
    private static final int WRITE_NFC_TAG_REQUEST_CODE = 1003;
    private static final int INFO_REQUEST_CODE = 1004;
    public static final String GCM_SENDER_ID = "737820980945";
    // Base URL of current openHAB connection
    private String openHABBaseUrl = "https://demo.openhab.org:8443/";
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
    // Enable/disable development mode
    private boolean isDeveloper;
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
    // Loopj
    private static MyAsyncHttpClient mAsyncHttpClient;
    // NFC Launch data
    private String mNfcData;
    // Pending NFC page
    private String mPendingNfcPage;
    // Drawer Layout
    private DrawerLayout mDrawerLayout;
    // Drawer Toggler
    private ActionBarDrawerToggle mDrawerToggle;
    // GCM Registration expiration
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;
    // Google Cloud Messaging
    private GoogleCloudMessaging mGcm;
    private OpenHABDrawerAdapter mDrawerAdapter;
    private String[] mDrawerTitles = {"First floor", "Seconf floor", "Cellar", "Garage"};
    private ListView mDrawerList;
    private List<OpenHABSitemap> mSitemapList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        // Check if we are in development mode
        isDeveloper = true;
        // Set default values, false means do it one time during the very first launch
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Set non-persistent HABDroid version preference to current version from application package
        try {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("default_openhab_appversion",
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName).commit();
        } catch (PackageManager.NameNotFoundException e1) {
            if (e1 != null)
                Log.d(TAG, e1.getMessage());
        }
        checkDiscoveryPermissions();
        checkVoiceRecognition();
        // initialize loopj async http client
        mAsyncHttpClient = new MyAsyncHttpClient(this);
        // Set the theme to one from preferences
        Util.setActivityTheme(this);
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        // Disable screen timeout if set in preferences
        if (mSettings.getBoolean("default_openhab_screentimeroff", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        // Fetch openHAB service type name from strings.xml
        openHABServiceType = getString(R.string.openhab_service_type);
        // Get username/password from preferences
        openHABUsername = mSettings.getString("default_openhab_username", null);
        openHABPassword = mSettings.getString("default_openhab_password", null);
        mAsyncHttpClient.setBasicAuth(openHABUsername, openHABPassword);
        mAsyncHttpClient.addHeader("Accept", "application/xml");
        mAsyncHttpClient.setTimeout(30000);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setProgressBarIndeterminateVisibility(true);
        if (!isDeveloper)
            Util.initCrittercism(getApplicationContext(), "5117659f59e1bd4ba9000004");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gcmRegisterBackground();
        // Enable app icon in action bar work as 'home'
//        this.getActionBar().setHomeButtonEnabled(true);
        pager = (OpenHABViewPager)findViewById(R.id.pager);
        pager.setScrollDurationFactor(2.5);
        pager.setOffscreenPageLimit(1);
        pagerAdapter = new OpenHABFragmentPagerAdapter(getSupportFragmentManager());
        pagerAdapter.setColumnsNumber(getResources().getInteger(R.integer.pager_columns));
        pagerAdapter.setOpenHABUsername(openHABUsername);
        pagerAdapter.setOpenHABPassword(openHABPassword);
        pager.setAdapter(pagerAdapter);
        pager.setOnPageChangeListener(pagerAdapter);
        MemorizingTrustManager.setResponder(this);
//        pager.setPageMargin(1);
//        pager.setPageMarginDrawable(android.R.color.darker_gray);
        // Check if we have openHAB page url in saved instance state?
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_navigation_drawer,
                R.string.app_name, R.string.app_name) {
            public void onDrawerClosed(View view) {
                Log.d(TAG, "onDrawerClosed");
            }
            public void onDrawerOpened(View drawerView) {
                Log.d(TAG, "onDrawerOpened");
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        if (savedInstanceState != null) {
            openHABBaseUrl = savedInstanceState.getString("openHABBaseUrl");
            sitemapRootUrl = savedInstanceState.getString("sitemapRootUrl");
        }
        mSitemapList = new ArrayList<OpenHABSitemap>();
        mDrawerAdapter = new OpenHABDrawerAdapter(this, R.layout.openhabdrawer_item, mSitemapList);
        mDrawerAdapter.setOpenHABUsername(openHABUsername);
        mDrawerAdapter.setOpenHABPassword(openHABPassword);
        mDrawerList.setAdapter(mDrawerAdapter);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int item, long l) {
                Log.d(TAG, "Drawer selected item " + String.valueOf(item));
                if (mSitemapList != null) {
                    Log.d(TAG, "This is sitemap " + mSitemapList.get(item).getLink());
                    mDrawerLayout.closeDrawers();
                    openSitemap(mSitemapList.get(item).getHomepageLink());
                }
            }
        });
//        mDrawerList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mDrawerTitles));
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
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
                }
            }
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
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        if (NfcAdapter.getDefaultAdapter(this) != null)
            NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, null, null);
        if (!TextUtils.isEmpty(mNfcData)) {
            Log.d(TAG, "We have NFC data from launch");
        }
        pagerAdapter.setColumnsNumber(getResources().getInteger(R.integer.pager_columns));
        FragmentManager fm = getSupportFragmentManager();
        stateFragment = (StateRetainFragment)fm.findFragmentByTag("stateFragment");
        if (stateFragment == null) {
            stateFragment = new StateRetainFragment();
            fm.beginTransaction().add(stateFragment, "stateFragment").commit();
            mOpenHABTracker = new OpenHABTracker(this, openHABServiceType, mServiceDiscoveryEnabled);
            mOpenHABTracker.start();
        } else {
            Log.d(TAG, "State fragment found");
            pagerAdapter.setFragmentList(stateFragment.getFragmentList());
            Log.d(TAG, String.format("Loaded %d fragments", stateFragment.getFragmentList().size()));
            pager.setCurrentItem(stateFragment.getCurrentPage());
            Log.d(TAG, String.format("Loaded current page = %d", stateFragment.getCurrentPage()));
        }
        if (!TextUtils.isEmpty(mPendingNfcPage)) {
            openNFCPageIfPending();
        }
    }

    public void openNFCPageIfPending() {
        int possiblePosition = pagerAdapter.getPositionByUrl(mPendingNfcPage);
        // If yes, then just switch to this page
        if (possiblePosition >= 0) {
            pager.setCurrentItem(possiblePosition);
            // If not, then open this page as new one
        } else {
            pagerAdapter.openPage(mPendingNfcPage);
            pager.setCurrentItem(pagerAdapter.getCount()-1);
        }
        mPendingNfcPage = null;
    }

    public void onOpenHABTracked(String baseUrl, String message) {
        if (message != null)
        Toast.makeText(getApplicationContext(), message,
                Toast.LENGTH_LONG).show();
        openHABBaseUrl = baseUrl;
        mDrawerAdapter.setOpenHABBaseUrl(openHABBaseUrl);
        pagerAdapter.setOpenHABBaseUrl(openHABBaseUrl);
        if (!TextUtils.isEmpty(mNfcData)) {
            onNfcTag(mNfcData);
            openNFCPageIfPending();
        } else {
            selectSitemap(baseUrl, false);
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
        mProgressDialog.dismiss();
    }

    /**
     * Get sitemaps from openHAB, if user already configured preffered sitemap
     * just open it. If no preffered sitemap is configured - let user select one.
     *
     * @param  baseUrl  an absolute base URL of openHAB to open
     * @return      void
     */

    private void selectSitemap(final String baseUrl, final boolean forceSelect) {
        Log.d(TAG, "Loading sitemap list from " + baseUrl + "rest/sitemaps");
        startProgressIndicator();
        mAsyncHttpClient.get(baseUrl + "rest/sitemaps", new DocumentHttpResponseHandler() {
            @Override
            public void onSuccess(Document document) {
                stopProgressIndicator();
                Log.d(TAG, "Response: " +  document.toString());
                mSitemapList.clear();
                mSitemapList.addAll(Util.parseSitemapList(document));
                if (mSitemapList.size() == 0) {
                    // Got an empty sitemap list!
                    Log.e(TAG, "openHAB returned empty sitemap list");
                    showAlertDialog(getString(R.string.error_empty_sitemap_list));
                    return;
                }
                mDrawerAdapter.notifyDataSetChanged();
                // If we are forced to do selection, just open selection dialog
                if (forceSelect) {
                    showSitemapSelectionDialog(mSitemapList);
                } else {
                    // Check if we have a sitemap configured to use
                    SharedPreferences settings =
                            PreferenceManager.getDefaultSharedPreferences(OpenHABMainActivity.this);
                    String configuredSitemap = settings.getString("default_openhab_sitemap", "");
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
                                preferencesEditor.putString("default_openhab_sitemap", mSitemapList.get(0).getName());
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
                            preferencesEditor.putString("default_openhab_sitemap", mSitemapList.get(0).getName());
                            preferencesEditor.commit();
                            openSitemap(mSitemapList.get(0).getHomepageLink());
                        } else {
                            Log.d(TAG, "Got multiply sitemaps, user have to select one");
                            showSitemapSelectionDialog(mSitemapList);
                        }
                    }
                }
            }
            @Override
            public void onFailure(Throwable error, String content) {
                stopProgressIndicator();
                if (error instanceof HttpResponseException) {
                    switch (((HttpResponseException) error).getStatusCode()) {
                        case 401:
                            showAlertDialog(getString(R.string.error_authentication_failed));
                            break;
                        default:
                            Log.e(TAG, String.format("Http code = %d", ((HttpResponseException) error).getStatusCode()));
                            break;
                    }
                } else if (error instanceof org.apache.http.conn.HttpHostConnectException) {
                    Log.e(TAG, "Error connecting to host");
                    if (error.getMessage() != null) {
                        Log.e(TAG, error.getMessage());
                        showAlertDialog(error.getMessage());
                    } else {
                        showAlertDialog(getString(R.string.error_connection_failed));
                    }
                } else if (error instanceof java.net.UnknownHostException) {
                    Log.e(TAG, "Unable to resolve hostname");
                    if (error.getMessage() != null) {
                        Log.e(TAG, error.getMessage());
                        showAlertDialog(error.getMessage());
                    } else {
                        showAlertDialog(getString(R.string.error_connection_failed));
                    }
                } else {
                    Log.e(TAG, error.getClass().toString());
                }
            }
        });

    }

    private void showSitemapSelectionDialog(final List<OpenHABSitemap> sitemapList) {
        Log.d(TAG, "Opening sitemap selection dialog");
        final List<String> sitemapNameList = new ArrayList<String>();;
        for (int i=0; i<sitemapList.size(); i++) {
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
                            preferencesEditor.putString("default_openhab_sitemap", sitemapList.get(item).getName());
                            preferencesEditor.commit();
                            openSitemap(sitemapList.get(item).getHomepageLink());
                        }
                    }).show();
        } catch (WindowManager.BadTokenException e) {
            Crittercism.logHandledException(e);
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
                preferencesEditor.putString("default_openhab_sitemap", "");
                preferencesEditor.commit();
                selectSitemap(openHABBaseUrl, true);
                return true;
            case android.R.id.home:
                Log.d(TAG, "Home selected");
                if (pager.getCurrentItem() > 0) {
                    pager.setCurrentItem(0);
                }
                return true;
            case R.id.mainmenu_openhab_clearcache:
                Log.d(TAG, "Restarting");
                // Get launch intent for application
                Intent restartIntent = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
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
                OpenHABWidgetListFragment currentFragment = pagerAdapter.getFragment(pager.getCurrentItem());
                writeTagIntent.putExtra("sitemapPage", currentFragment.getDisplayPageUrl());
                startActivityForResult(writeTagIntent, WRITE_NFC_TAG_REQUEST_CODE);
                Util.overridePendingTransition(this, false);
                return true;
            case R.id.mainmenu_openhab_info:
                Intent infoIntent = new Intent(this.getApplicationContext(), OpenHABInfoActivity.class);
                infoIntent.putExtra("openHABBaseUrl", openHABBaseUrl);
                infoIntent.putExtra("username", openHABUsername);
                infoIntent.putExtra("password", openHABPassword);
                startActivityForResult(infoIntent, INFO_REQUEST_CODE);
                Util.overridePendingTransition(this, false);
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
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                // Finish current activity
                finish();
                // Start launch activity
                startActivity(restartIntent);
                break;
            case WRITE_NFC_TAG_REQUEST_CODE:
                Log.d(TAG, "Got back from Write NFC tag");
                break;
            case VOICE_RECOGNITION_REQUEST_CODE:
                Log.d(TAG, "Got back from Voice recognition");
                setProgressBarIndeterminateVisibility(false);
                if(resultCode == RESULT_OK) {
                    ArrayList<String> textMatchList = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (!textMatchList.isEmpty()) {
                        Log.d(TAG, textMatchList.get(0));
                        Log.d(TAG, "Recognized text: " + textMatchList.get(0));
                        Toast.makeText(this, "I recognized: " + textMatchList.get(0),
                                Toast.LENGTH_LONG).show();
                        sendItemCommand("VoiceCommand", textMatchList.get(0));
                    } else {
                        Log.d(TAG, "Voice recognition returned empty set");
                        Toast.makeText(this, "I can't read you!",
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.d(TAG, "A voice recognition error occured");
                    Toast.makeText(this, "A voice recognition error occured",
                            Toast.LENGTH_LONG).show();
                }
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
        savedInstanceState.putString("openHABBaseUrl", openHABBaseUrl);
        savedInstanceState.putString("sitemapRootUrl", sitemapRootUrl);
        savedInstanceState.putInt("currentFragment", pager.getCurrentItem());
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Overriding onStart to enable Google Analytics stats collection
     */
    @Override
    public void onStart() {
        super.onStart();
        // Start activity tracking via Google Analytics
        if (!isDeveloper)
            EasyTracker.getInstance().activityStart(this);
    }

    /**
     * Overriding onStop to enable Google Analytics stats collection
     */
    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        // Stop activity tracking via Google Analytics
        if (!isDeveloper)
            EasyTracker.getInstance().activityStop(this);
        if (mOpenHABTracker != null)
            mOpenHABTracker.stop();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        Log.d(TAG, String.format("Saving %d fragments", pagerAdapter.getFragmentList().size()));
        Log.d(TAG, String.format("Saving current page = %d", pager.getCurrentItem()));
        stateFragment.setFragmentList(pagerAdapter.getFragmentList());
        stateFragment.setCurrentPage(pager.getCurrentItem());
//        Runnable can = new Runnable() {
//            public void run() {
//                mAsyncHttpClient.cancelRequests(OpenHABMainActivity.this, true);
//            }
//        };
//        new Thread(can).start();
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
            StringEntity se = new StringEntity(command);
            mAsyncHttpClient.post(this, openHABBaseUrl + "rest/items/" + itemName, se, "text/plain", new AsyncHttpResponseHandler() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Command was sent successfully");
                }
                @Override
                public void onFailure(Throwable error, String errorResponse) {
                    Log.e(TAG, "Got command error " + error.getMessage());
                    if (errorResponse != null)
                        Log.e(TAG, "Error response = " + errorResponse);
                }
            });
        } catch (UnsupportedEncodingException e) {
            if (e.getMessage() != null)
                Log.e(TAG, e.getMessage());
        }
    }

    public void onWidgetSelectedListener(OpenHABLinkedPage linkedPage, OpenHABWidgetListFragment source) {
        Log.i(TAG, "Got widget link = " + linkedPage.getLink());
        Log.i(TAG, String.format("Link came from fragment on position %d", source.getPosition()));
        pagerAdapter.openPage(linkedPage.getLink(), source.getPosition() + 1);
        pager.setCurrentItem(pagerAdapter.getCount()-1);
        setTitle(linkedPage.getTitle());
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, String.format("onBackPressed() I'm at the %d page", pager.getCurrentItem()));
        if (pager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            pager.setCurrentItem(pager.getCurrentItem() - 1, true);
            setTitle(pagerAdapter.getPageTitle(pager.getCurrentItem()));
        }
    }

    public void startProgressIndicator() {
        setProgressBarIndeterminateVisibility(true);
    }

    public void stopProgressIndicator() {
        setProgressBarIndeterminateVisibility(false);
    }

    private void launchVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        // Display an hint to the user about what he should say.
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "openHAB, at your command!");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }


    private void showAlertDialog(String alertMessage) {
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
        if (mSettings.getBoolean("default_openhab_sslcert", false))
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

    public static MyAsyncHttpClient getAsyncHttpClient() {
        return mAsyncHttpClient;
    }

    private void gcmRegisterBackground() {
        // We need settings
        if (mSettings == null)
            return;
        // We need remote URL
        String remoteUrl = mSettings.getString("default_openhab_alturl", null);
        if (TextUtils.isEmpty(remoteUrl))
            return;
        // We need it to be my.oh
        if (!remoteUrl.toLowerCase().startsWith("https://my.openhab.org"))
            return;
        // Finally, all sanity is done
        if (mGcm == null)
            mGcm = GoogleCloudMessaging.getInstance(getApplicationContext());
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String regId = null;
                try {
                    regId = mGcm.register(GCM_SENDER_ID);
                    String deviceModel = URLEncoder.encode(Build.MODEL, "UTF-8");
                    String deviceId = Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);
                    String regUrl = "https://my.openhab.org/addAndroidRegistration?deviceId=" + deviceId +
                            "&deviceModel=" + deviceModel + "&regId=" + regId;
                    mAsyncHttpClient.get(getApplicationContext(), regUrl, new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(String response) {
                            Log.d(TAG, "GCM reg id success");
                        }
                        @Override
                        public void onFailure(Throwable error, String errorResponse) {
                            Log.e(TAG, "GCM reg id error: " + error.getMessage());
                            if (errorResponse != null)
                                Log.e(TAG, "Error response = " + errorResponse);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }
                return regId;
            }
            @Override
            protected void onPostExecute(String regId) {
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,null, null, null);
    }
}

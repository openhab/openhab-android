/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.TextureView;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;
import org.w3c.dom.Text;

import static org.openhab.habdroid.util.Util.getHostFromUrl;

/**
 * This is a class to provide preferences activity for application.
 */
public class OpenHABPreferencesActivity extends AppCompatActivity {
    public static final String RESULT_EXTRA_THEME_CHANGED = "theme_changed";
    public static final String RESULT_EXTRA_SITEMAP_CLEARED = "sitemap_cleared";
    public static final String START_EXTRA_OPENHAB_VERSION = "openhab_version";
    private static final String STATE_KEY_RESULT = "result";

    private static final String TAG = OpenHABPreferencesActivity.class.getSimpleName();
    private Intent mResultIntent;
    private static int mOpenhabVersion;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_prefs);

        Toolbar toolbar = (Toolbar) findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            mResultIntent = new Intent();
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.prefs_container, new MainSettingsFragment())
                    .commit();
        } else {
            mResultIntent = savedInstanceState.getParcelable(STATE_KEY_RESULT);
        }
        setResult(RESULT_OK, mResultIntent);

        mOpenhabVersion = getIntent().getIntExtra(START_EXTRA_OPENHAB_VERSION, 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_KEY_RESULT, mResultIntent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
                    NavUtils.navigateUpFromSameTask(this);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }

    public void handleThemeChange() {
        mResultIntent.putExtra(RESULT_EXTRA_THEME_CHANGED, true);
        recreate();
    }

    public void openSubScreen(AbstractSettingsFragment subScreenFragment) {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.prefs_container, subScreenFragment)
                .addToBackStack(null)
                .commit();
    }

    private static abstract class AbstractSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            updateAndInitPreferences();
        }

        @Override
        public void onStart() {
            super.onStart();
            getParentActivity().getSupportActionBar().setTitle(getTitleResId());
        }

        protected abstract void updateAndInitPreferences();

        protected abstract @StringRes int getTitleResId();

        protected OpenHABPreferencesActivity getParentActivity() {
            return (OpenHABPreferencesActivity) getActivity();
        }

        protected String getPreferenceString(Preference preference, String defValue) {
            return getPreferenceString(preference.getKey(), defValue);
        }

        protected String getPreferenceString(String prefKey, String defValue) {
            return getPreferenceScreen().getSharedPreferences().getString(prefKey, defValue);
        }

        protected boolean isConnectionHttps(String url) {
            return url.startsWith("https://");
        }

        protected boolean hasConnectionBasicAuthentication(String user, String password) {
            return ! TextUtils.isEmpty(user) && ! TextUtils.isEmpty(password);
        }

        protected boolean hasClientCertificate() {
            return ! TextUtils.isEmpty(getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT)
                    .getSharedPreferences().getString(Constants.PREFERENCE_SSLCLIENTCERT, ""));
        }

        protected boolean isConnectionSecure(String url, String user, String password) {
            return isConnectionHttps(url) &&
                    (hasConnectionBasicAuthentication(user, password) || hasClientCertificate());
        }

        protected boolean isWeakPassword(String password) {
            return password.length() < 8;
        }
    }

    public static class MainSettingsFragment extends AbstractSettingsFragment {
        @Override
        public void onStart() {
            super.onStart();
            updateConnectionSummary(Constants.SUBSCREEN_LOCAL_CONNECTION,
                    Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                    Constants.PREFERENCE_LOCAL_PASSWORD);
            updateConnectionSummary(Constants.SUBSCREEN_REMOTE_CONNECTION,
                    Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                    Constants.PREFERENCE_REMOTE_PASSWORD);
        }

        @Override
        protected @StringRes
        int getTitleResId() {
            return R.string.action_settings;
        }

        @Override
        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.preferences);

            final Preference subScreenLocalConn = findPreference(Constants.SUBSCREEN_LOCAL_CONNECTION);
            final Preference subScreenRemoteConn = findPreference(Constants.SUBSCREEN_REMOTE_CONNECTION);
            final Preference themePreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_THEME);
            final Preference clearCachePreference = getPreferenceScreen().findPreference(Constants
                    .PREFERENCE_CLEAR_CACHE);
            final Preference clearDefaultSitemapPreference = getPreferenceScreen().findPreference
                    (Constants.PREFERENCE_CLEAR_DEFAULT_SITEMAP);
            final Preference ringtonePreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_TONE);
            final Preference vibrationPreference =
                    getPreferenceScreen().findPreference(Constants.PREFERENCE_NOTIFICATION_VIBRATION);

            String currentDefaultSitemap = clearDefaultSitemapPreference.getSharedPreferences().getString(Constants
                    .PREFERENCE_SITEMAP_NAME, "");
            String currentDefaultSitemapLabel = clearDefaultSitemapPreference.getSharedPreferences().getString(Constants
                    .PREFERENCE_SITEMAP_LABEL, "");
            if (currentDefaultSitemap.isEmpty()) {
                onNoDefaultSitemap(clearDefaultSitemapPreference);
            } else {
                clearDefaultSitemapPreference.setSummary(getString(
                        R.string.settings_current_default_sitemap, currentDefaultSitemapLabel));
            }

            updateConnectionSummary(Constants.SUBSCREEN_LOCAL_CONNECTION,
                    Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                    Constants.PREFERENCE_LOCAL_PASSWORD);
            updateConnectionSummary(Constants.SUBSCREEN_REMOTE_CONNECTION,
                    Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                    Constants.PREFERENCE_REMOTE_PASSWORD);
            updateRingtonePreferenceSummary(ringtonePreference, ringtonePreference
                    .getSharedPreferences().getString(Constants.PREFERENCE_TONE, ""));
            updateVibrationPreferenceIcon(vibrationPreference, vibrationPreference
                    .getSharedPreferences().getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION, ""));

            subScreenLocalConn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getParentActivity().openSubScreen(new LocalConnectionSettingsFragment());
                    return false;
                }
            });

            subScreenRemoteConn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getParentActivity().openSubScreen(new RemoteConnectionSettingsFragment());
                    return false;
                }
            });

            themePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    getParentActivity().handleThemeChange();
                    return true;
                }
            });

            clearCachePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // Get launch intent for application
                    Intent restartIntent = getActivity().getPackageManager()
                            .getLaunchIntentForPackage(getActivity().getBaseContext().getPackageName());
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    // Finish current activity
                    getActivity().finish();
                    CacheManager.getInstance(getActivity()).clearCache();
                    // Start launch activity
                    startActivity(restartIntent);
                    // Start launch activity
                    return true;
                }
            });

            clearDefaultSitemapPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences.Editor edit = preference.getSharedPreferences().edit();
                    edit.putString(Constants.PREFERENCE_SITEMAP_NAME, "");
                    edit.putString(Constants.PREFERENCE_SITEMAP_LABEL, "");
                    edit.apply();

                    onNoDefaultSitemap(preference);
                    getParentActivity().mResultIntent.putExtra(RESULT_EXTRA_SITEMAP_CLEARED, true);
                    return true;
                }
            });

            ringtonePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateRingtonePreferenceSummary(preference, newValue);
                    return true;
                }
            });

            vibrationPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateVibrationPreferenceIcon(preference, newValue);
                    return true;
                }
            });

            final PreferenceScreen ps = getPreferenceScreen();

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "Removing fullscreen pref as device isn't running Kitkat or higher");
                Preference fullscreenPreference = ps.findPreference(Constants.PREFERENCE_FULLSCREEN);
                getParent(fullscreenPreference).removePreference(fullscreenPreference);
            }

            if (!CloudMessagingHelper.isSupported()) {
                Log.d(TAG, "Removing full-only prefs");
                getParent(ringtonePreference).removePreference(ringtonePreference);
                Preference vibrationPreference =
                        ps.findPreference(Constants.PREFERENCE_NOTIFICATION_VIBRATION);
                getParent(vibrationPreference).removePreference(vibrationPreference);
            }

            if (mOpenhabVersion == 1) {
                Log.d(TAG, "Removing prefs that aren't available in openHAB 1");
                Preference iconFormatPreference =
                        ps.findPreference(Constants.PREFERENCE_ICON_FORMAT);
                getParent(iconFormatPreference).removePreference(iconFormatPreference);
                Preference chartScalingPreference =
                        ps.findPreference(Constants.PREFERENCE_CHART_SCALING);
                getParent(chartScalingPreference).removePreference(chartScalingPreference);
            }
        }

        /**
         * @author https://stackoverflow.com/a/17633389
         */
        private PreferenceGroup getParent(Preference preference) {
            return getParent(getPreferenceScreen(), preference);
        }

        /**
         * @author https://stackoverflow.com/a/17633389
         */
        private PreferenceGroup getParent(PreferenceGroup root, Preference preference) {
            for (int i = 0; i < root.getPreferenceCount(); i++) {
                Preference p = root.getPreference(i);
                if (p == preference) {
                    return root;
                }
                if (p instanceof PreferenceGroup) {
                    PreferenceGroup parent = getParent((PreferenceGroup)p, preference);
                    if (parent != null) {
                        return parent;
                    }
                }
            }
            return null;
        }

        private void onNoDefaultSitemap(Preference pref) {
            pref.setEnabled(false);
            pref.setSummary(R.string.settings_no_default_sitemap);
        }

        private void updateRingtonePreferenceSummary(Preference pref, Object newValue) {
            String value = (String) newValue;
            if (TextUtils.isEmpty(value)) {
                pref.setIcon(R.drawable.ic_notifications_off_grey_24dp);
                pref.setSummary(R.string.settings_ringtone_none);
            } else {
                pref.setIcon(R.drawable.ic_notifications_active_grey_24dp);
                Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), Uri.parse(value));
                if (ringtone != null) {
                    pref.setSummary(ringtone.getTitle(getActivity()));
                }
            }
        }

        private void updateVibrationPreferenceIcon(Preference pref, Object newValue) {
            String value = (String) newValue;
            if (value.equals(getString(R.string.settings_notification_vibration_value_off))) {
                pref.setIcon(R.drawable.ic_smartphone_grey_24dp);
            } else {
                pref.setIcon(R.drawable.ic_vibration_grey_24dp);
            }
        }

        private void updateConnectionSummary(String subscreenPrefKey, String urlPrefKey,
                                            String userPrefKey, String passwordPrefKey) {
            Preference pref = findPreference(subscreenPrefKey);
            String url = pref.getSharedPreferences().getString(urlPrefKey, "");
            String summary;
            if (TextUtils.isEmpty(url)) {
                summary = getString(R.string.info_not_set);
            } else {
                if (isConnectionSecure(url, getPreferenceString(userPrefKey, ""),
                        getPreferenceString(passwordPrefKey, ""))) {
                    summary = getString(R.string.settings_connection_summary,
                            beautifyUrl(getHostFromUrl(url)));
                } else {
                    summary = getString(R.string.settings_insecure_connection_summary,
                            beautifyUrl(getHostFromUrl(url)));
                }
            }
            pref.setSummary(summary);
        }

        private String beautifyUrl(String url) {
            return url.contains("myopenhab.org") ? "myopenHAB" : url;
        }
    }

    private static abstract class ConnectionSettingsFragment extends AbstractSettingsFragment {
        private void updateTextPreferenceSummary(Preference textPreference,
                                                 @StringRes int summaryFormatResId,
                                                 String newValue, boolean isPassword) {
            if (newValue == null) {
                newValue = getPreferenceString(textPreference, "");
            }
            if (newValue.isEmpty()) {
                newValue = getString(R.string.info_not_set);
            } else if (isPassword) {
                newValue = getString(R.string.password_placeholder);
            }

            textPreference.setSummary(summaryFormatResId != 0
                    ? getString(summaryFormatResId, newValue) : newValue);
        }

        protected void initEditorPreference(String key, @StringRes final int summaryFormatResId,
                                            final boolean isPassword) {
            Preference pref = getPreferenceScreen().findPreference(key);
            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateTextPreferenceSummary(preference, summaryFormatResId, (String) newValue, isPassword);
                    return true;
                }
            });
            updateTextPreferenceSummary(pref, summaryFormatResId, null, isPassword);
        }

        protected void setUrlIconColor(String urlPrefKey) {
            Preference pref = getPreferenceScreen().findPreference(urlPrefKey);
            String url = getPreferenceString(pref, "");
            if (TextUtils.isEmpty(url)) {
                pref.setIcon(R.drawable.ic_earth_grey_24dp);
            } else if (isConnectionHttps(url)) {
                pref.setIcon(R.drawable.ic_earth_green_24dp);
            } else {
                pref.setIcon(R.drawable.ic_earth_red_24dp);
            }
        }

        protected void setUserIconColor(String urlPrefKey, String userPrefKey) {
            Preference userPref = getPreferenceScreen().findPreference(userPrefKey);
            if (TextUtils.isEmpty(getPreferenceString(urlPrefKey, ""))) {
                userPref.setIcon(R.drawable.ic_person_grey_24dp);
            } else if (TextUtils.isEmpty(getPreferenceString(userPref, ""))) {
                userPref.setIcon(R.drawable.ic_person_red_24dp);
            } else {
                userPref.setIcon(R.drawable.ic_person_green_24dp);
            }
        }

        protected void setPasswordIconColor(String urlPrefKey, String passwordPrefKey) {
            Preference passwordPref = getPreferenceScreen().findPreference(passwordPrefKey);
            String password = getPreferenceString(passwordPref, "");
            if (TextUtils.isEmpty(getPreferenceString(urlPrefKey, ""))) {
                passwordPref.setIcon(R.drawable.ic_security_grey_24dp);
            } else if (TextUtils.isEmpty(password)) {
                passwordPref.setIcon(R.drawable.ic_security_red_24dp);
            } else if (isWeakPassword(password)) {
                passwordPref.setIcon(R.drawable.ic_security_orange_24dp);
            } else {
                passwordPref.setIcon(R.drawable.ic_security_green_24dp);
            }
        }
    }

    public static class LocalConnectionSettingsFragment extends ConnectionSettingsFragment {
        @Override
        protected @StringRes int getTitleResId() {
            return R.string.settings_openhab_connection;
        }

        @Override
        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.local_connection_preferences);

            initEditorPreference(Constants.PREFERENCE_LOCAL_URL, R.string.settings_openhab_url_summary, false);
            setUrlIconColor(Constants.PREFERENCE_LOCAL_URL);
            initEditorPreference(Constants.PREFERENCE_LOCAL_USERNAME, 0, false);
            setUserIconColor(Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME);
            initEditorPreference(Constants.PREFERENCE_LOCAL_PASSWORD, 0, true);
            setPasswordIconColor(Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_PASSWORD);
        }
    }

    public static class RemoteConnectionSettingsFragment extends ConnectionSettingsFragment {
        @Override
        protected @StringRes int getTitleResId() {
            return R.string.settings_openhab_alt_connection;
        }

        @Override
        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.remote_connection_preferences);

            initEditorPreference(Constants.PREFERENCE_REMOTE_URL, R.string.settings_openhab_alturl_summary, false);
            initEditorPreference(Constants.PREFERENCE_REMOTE_USERNAME, 0, false);
            initEditorPreference(Constants.PREFERENCE_REMOTE_PASSWORD, 0, true);
        }
    }
}

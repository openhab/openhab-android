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
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.openhab.habdroid.R;
import org.openhab.habdroid.core.CloudMessagingHelper;
import org.openhab.habdroid.model.ServerProperties;
import org.openhab.habdroid.util.CacheManager;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.util.BitSet;

import static org.openhab.habdroid.util.Constants.PREV_SERVER_FLAGS;
import static org.openhab.habdroid.util.Util.getHostFromUrl;

/**
 * This is a class to provide preferences activity for application.
 */
public class PreferencesActivity extends AppCompatActivity {
    public static final String RESULT_EXTRA_THEME_CHANGED = "theme_changed";
    public static final String RESULT_EXTRA_SITEMAP_CLEARED = "sitemap_cleared";
    public static final String START_EXTRA_SERVER_PROPERTIES = "server_properties";
    private static final String STATE_KEY_RESULT = "result";

    private static final String TAG = PreferencesActivity.class.getSimpleName();
    private Intent mResultIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_prefs);

        Toolbar toolbar = findViewById(R.id.openhab_toolbar);
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
            default:
                return super.onOptionsItemSelected(item);
        }
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

    @VisibleForTesting
    public abstract static class AbstractSettingsFragment extends PreferenceFragment {
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

        protected PreferencesActivity getParentActivity() {
            return (PreferencesActivity) getActivity();
        }

        protected String getPreferenceString(Preference preference, String defValue) {
            return getPreferenceString(preference.getKey(), defValue);
        }

        protected String getPreferenceString(String prefKey, String defValue) {
            return getPreferenceScreen().getSharedPreferences().getString(prefKey, defValue);
        }

        protected int getPreferenceInt(Preference preference, int defValue) {
            return getPreferenceInt(preference.getKey(), defValue);
        }

        protected int getPreferenceInt(String prefKey, int defValue) {
            return getPreferenceScreen().getSharedPreferences().getInt(prefKey, defValue);
        }

        protected boolean getPreferenceBool(Preference preference, boolean defValue) {
            return getPreferenceBool(preference.getKey(), defValue);
        }

        protected boolean getPreferenceBool(String prefKey, boolean defValue) {
            return getPreferenceScreen().getSharedPreferences().getBoolean(prefKey, defValue);
        }

        protected boolean isConnectionHttps(String url) {
            return url.startsWith("https://");
        }

        protected boolean hasConnectionBasicAuthentication(String user, String password) {
            return !TextUtils.isEmpty(user) && !TextUtils.isEmpty(password);
        }

        protected boolean hasClientCertificate() {
            return !TextUtils.isEmpty(getPreferenceString(Constants.PREFERENCE_SSLCLIENTCERT, ""));
        }

        protected boolean isConnectionSecure(String url, String user, String password) {
            if (!isConnectionHttps(url)) {
                return false;
            }
            return hasConnectionBasicAuthentication(user, password) || hasClientCertificate();
        }


        /**
         * Password is considered strong when it is at least 8 chars long and contains 3 from those
         * 4 categories:
         *      * lowercase
         *      * uppercase
         *      * numerics
         *      * other
         * @param password
         */
        @VisibleForTesting
        public static boolean isWeakPassword(String password) {
            if (password.length() < 8) {
                return true;
            }
            BitSet groups = new BitSet();
            for (int i = 0; i < password.length(); i++) {
                char c = password.charAt(i);
                if (Character.isLetter(c) && Character.isLowerCase(c)) {
                    groups.set(0);
                } else if (Character.isLetter(c) && Character.isUpperCase(c)) {
                    groups.set(1);
                } else if (Character.isDigit(c)) {
                    groups.set(2);
                } else {
                    groups.set(3);
                }
            }

            return groups.cardinality() < 3;
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
        protected @StringRes int getTitleResId() {
            return R.string.action_settings;
        }

        @Override
        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.preferences);

            final Preference localConnPref = findPreference(Constants.SUBSCREEN_LOCAL_CONNECTION);
            final Preference remoteConnPref = findPreference(Constants.SUBSCREEN_REMOTE_CONNECTION);
            final Preference themePref = findPreference(Constants.PREFERENCE_THEME);
            final Preference clearCachePref = findPreference(Constants.PREFERENCE_CLEAR_CACHE);
            final Preference clearDefaultSitemapPref =
                    findPreference(Constants.PREFERENCE_CLEAR_DEFAULT_SITEMAP);
            final Preference ringtonePref = findPreference(Constants.PREFERENCE_TONE);
            final Preference alarmClockPrefCat = findPreference(Constants.PREFERENCE_ALARM_CLOCK);
            final Preference alarmClockEnabledPref = findPreference(Constants.PREFERENCE_ALARM_CLOCK_ENABLED);
            final Preference alarmClockItemPref = findPreference(Constants.PREFERENCE_ALARM_CLOCK_ITEM);
            final Preference vibrationPref =
                    findPreference(Constants.PREFERENCE_NOTIFICATION_VIBRATION);
            final Preference ringtoneVibrationPref =
                    findPreference(Constants.PREFERENCE_NOTIFICATION_TONE_VIBRATION);
            final Preference viewLogPref = findPreference(Constants.PREFERENCE_LOG);
            final SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();

            String currentDefaultSitemap = prefs.getString(Constants.PREFERENCE_SITEMAP_NAME, "");
            String currentDefaultSitemapLabel = prefs.getString(Constants.PREFERENCE_SITEMAP_LABEL, "");
            if (currentDefaultSitemap.isEmpty()) {
                onNoDefaultSitemap(clearDefaultSitemapPref);
            } else {
                clearDefaultSitemapPref.setSummary(getString(
                        R.string.settings_current_default_sitemap, currentDefaultSitemapLabel));
            }

            updateConnectionSummary(Constants.SUBSCREEN_LOCAL_CONNECTION,
                    Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                    Constants.PREFERENCE_LOCAL_PASSWORD);
            updateConnectionSummary(Constants.SUBSCREEN_REMOTE_CONNECTION,
                    Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                    Constants.PREFERENCE_REMOTE_PASSWORD);
            updateRingtonePreferenceSummary(ringtonePref,
                    prefs.getString(Constants.PREFERENCE_TONE, ""));
            updateVibrationPreferenceIcon(vibrationPref,
                    prefs.getString(Constants.PREFERENCE_NOTIFICATION_VIBRATION, ""));

            localConnPref.setOnPreferenceClickListener(preference -> {
                getParentActivity().openSubScreen(new LocalConnectionSettingsFragment());
                return false;
            });

            remoteConnPref.setOnPreferenceClickListener(preference -> {
                getParentActivity().openSubScreen(new RemoteConnectionSettingsFragment());
                return false;
            });

            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                getParentActivity().handleThemeChange();
                return true;
            });

            clearCachePref.setOnPreferenceClickListener(preference -> {
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
            });

            clearDefaultSitemapPref.setOnPreferenceClickListener(preference -> {
                SharedPreferences.Editor edit = preference.getSharedPreferences().edit();
                edit.putString(Constants.PREFERENCE_SITEMAP_NAME, "");
                edit.putString(Constants.PREFERENCE_SITEMAP_LABEL, "");
                edit.apply();

                onNoDefaultSitemap(preference);
                getParentActivity().mResultIntent.putExtra(RESULT_EXTRA_SITEMAP_CLEARED, true);
                return true;
            });

            ringtonePref.setOnPreferenceChangeListener((pref, newValue) -> {
                updateRingtonePreferenceSummary(pref, newValue);
                return true;
            });

            vibrationPref.setOnPreferenceChangeListener((pref, newValue) -> {
                updateVibrationPreferenceIcon(pref, newValue);
                return true;
            });

            ringtoneVibrationPref.setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(android.provider.Settings.ACTION_SETTINGS);
                i.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
                startActivity(i);
                return true;
            });

            viewLogPref.setOnPreferenceClickListener(preference -> {
                Intent logIntent = new Intent(preference.getContext(), LogActivity.class);
                startActivity(logIntent);
                return true;
            });

            final PreferenceScreen ps = getPreferenceScreen();

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "Removing fullscreen pref as device isn't running Kitkat or higher");
                Preference fullscreenPreference = ps.findPreference(Constants.PREFERENCE_FULLSCREEN);
                getParent(fullscreenPreference).removePreference(fullscreenPreference);
            }

            if (CloudMessagingHelper.isSupported()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "Removing notification prefs for < 25");
                    getParent(ringtonePref).removePreference(ringtonePref);
                    getParent(vibrationPref).removePreference(vibrationPref);
                } else {
                    Log.d(TAG, "Removing notification prefs for >= 25");
                    getParent(ringtoneVibrationPref).removePreference(ringtoneVibrationPref);
                }
            } else {
                Log.d(TAG, "Removing all notification prefs");
                getParent(ringtonePref).removePreference(ringtonePref);
                getParent(vibrationPref).removePreference(vibrationPref);
                getParent(ringtoneVibrationPref).removePreference(ringtoneVibrationPref);
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "Removing alarm clock prefs");
                getPreferenceScreen().removePreference(alarmClockPrefCat);
            } else {
                updateAlarmClockEnabledPreferenceIcon(alarmClockEnabledPref, getPreferenceBool(alarmClockEnabledPref, false));
                alarmClockEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    updateAlarmClockEnabledPreferenceIcon(preference, newValue);
                    return true;
                });

                setEditorSummary(alarmClockItemPref, getPreferenceString(alarmClockItemPref, ""));
                alarmClockItemPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    setEditorSummary(preference, newValue);
                    return true;
                });
            }

            final ServerProperties props =
                    getActivity().getIntent().getParcelableExtra(START_EXTRA_SERVER_PROPERTIES);
            final int flags = props != null ? props.flags() :
                    getPreferenceScreen().getSharedPreferences().getInt(PREV_SERVER_FLAGS, 0);

            if ((flags & ServerProperties.SERVER_FLAG_ICON_FORMAT_SUPPORT) == 0) {
                Preference iconFormatPreference =
                        ps.findPreference(Constants.PREFERENCE_ICON_FORMAT);
                getParent(iconFormatPreference).removePreference(iconFormatPreference);
            }
            if ((flags & ServerProperties.SERVER_FLAG_CHART_SCALING_SUPPORT) == 0) {
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
                    PreferenceGroup parent = getParent((PreferenceGroup) p, preference);
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
            boolean noVibration = newValue.equals(
                    getString(R.string.settings_notification_vibration_value_off));
            pref.setIcon(noVibration
                    ? R.drawable.ic_smartphone_grey_24dp : R.drawable.ic_vibration_grey_24dp);
        }

        private void updateAlarmClockEnabledPreferenceIcon(Preference pref, Object newValue) {
            boolean enabled = (boolean) newValue;
            pref.setIcon(enabled ? R.drawable.ic_alarm_grey_24dp : R.drawable.ic_alarm_off_grey_24dp);
        }

        private void setEditorSummary(Preference pref, Object newValue) {
            String itemName = (String) newValue;
            boolean isSet = !TextUtils.isEmpty(itemName);
            pref.setSummary(isSet ? itemName : getString(R.string.info_not_set));
        }

        private void updateConnectionSummary(String subscreenPrefKey, String urlPrefKey,
                String userPrefKey, String passwordPrefKey) {
            Preference pref = findPreference(subscreenPrefKey);
            String url = getPreferenceString(urlPrefKey, "");
            final String summary;
            if (TextUtils.isEmpty(url)) {
                summary = getString(R.string.info_not_set);
            } else if (isConnectionSecure(url, getPreferenceString(userPrefKey, ""),
                    getPreferenceString(passwordPrefKey, ""))) {
                summary = getString(R.string.settings_connection_summary,
                        beautifyUrl(getHostFromUrl(url)));
            } else {
                summary = getString(R.string.settings_insecure_connection_summary,
                        beautifyUrl(getHostFromUrl(url)));
            }
            pref.setSummary(summary);
        }

        public static @Nullable String beautifyUrl(@Nullable String url) {
            return url != null && url.contains("myopenhab.org") ? "myopenHAB" : url;
        }
    }

    private abstract static class ConnectionSettingsFragment extends AbstractSettingsFragment {
        private Preference mUrlPreference;
        private Preference mUserNamePreference;
        private Preference mPasswordPreference;

        private interface IconColorGenerator {
            Integer getIconColor();
        }

        private interface PrefSummaryGenerator {
            CharSequence getSummary(String value);
        }

        protected void initPreferences(String urlPrefKey, String userNamePrefKey,
                String passwordPrefKey, @StringRes int urlSummaryFormatResId) {
            mUrlPreference = initEditor(urlPrefKey, R.drawable.ic_earth_grey_24dp, value -> {
                if (TextUtils.isEmpty(value)) {
                    value = getString(R.string.info_not_set);
                }
                return getString(urlSummaryFormatResId, value);
            });
            mUserNamePreference = initEditor(userNamePrefKey, R.drawable.ic_person_grey_24dp,
                    value -> TextUtils.isEmpty(value) ? getString(R.string.info_not_set) : value);
            mPasswordPreference = initEditor(passwordPrefKey,
                    R.drawable.ic_security_grey_24dp, value -> {
                        @StringRes int resId = TextUtils.isEmpty(value) ? R.string.info_not_set
                                : isWeakPassword(value) ? R.string.settings_openhab_password_summary_weak
                                : R.string.settings_openhab_password_summary_strong;
                        return getString(resId);
                    });

            updateIconColors(getPreferenceString(urlPrefKey, ""),
                    getPreferenceString(userNamePrefKey, ""),
                    getPreferenceString(passwordPrefKey, ""));
        }

        private Preference initEditor(String key, @DrawableRes int iconResId,
                PrefSummaryGenerator summaryGenerator) {
            Preference preference = getPreferenceScreen().findPreference(key);
            preference.setIcon(DrawableCompat.wrap(
                    ContextCompat.getDrawable(getActivity(), iconResId)));
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                updateIconColors(getActualValue(pref, newValue, mUrlPreference),
                        getActualValue(pref, newValue, mUserNamePreference),
                        getActualValue(pref, newValue, mPasswordPreference));
                pref.setSummary(summaryGenerator.getSummary((String) newValue));
                return true;
            });
            preference.setSummary(summaryGenerator.getSummary(getPreferenceString(key, "")));
            return preference;
        }

        private String getActualValue(Preference pref, Object newValue, Preference reference) {
            return pref == reference ? (String) newValue : getPreferenceString(reference, "");
        }

        private void updateIconColors(String url, String userName, String password) {
            updateIconColor(mUrlPreference, () -> {
                if (!TextUtils.isEmpty(url)) {
                    return isConnectionHttps(url) ? R.color.pref_icon_green : R.color.pref_icon_red;
                }
                return null;
            });
            updateIconColor(mUserNamePreference, () -> {
                if (!TextUtils.isEmpty(url)) {
                    return TextUtils.isEmpty(userName)
                            ? R.color.pref_icon_red : R.color.pref_icon_green;
                }
                return null;
            });
            updateIconColor(mPasswordPreference, () -> {
                if (!TextUtils.isEmpty(url)) {
                    if (TextUtils.isEmpty(password)) {
                        return R.color.pref_icon_red;
                    } else if (isWeakPassword(password)) {
                        return R.color.pref_icon_orange;
                    } else {
                        return R.color.pref_icon_green;
                    }
                }
                return null;
            });
        }

        private void updateIconColor(Preference pref, IconColorGenerator colorGenerator) {
            Drawable icon = pref.getIcon();
            Integer colorResId = colorGenerator.getIconColor();
            if (colorResId != null) {
                DrawableCompat.setTint(icon, ContextCompat.getColor(pref.getContext(), colorResId));
            } else {
                DrawableCompat.setTintList(icon, null);
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
            initPreferences(Constants.PREFERENCE_LOCAL_URL, Constants.PREFERENCE_LOCAL_USERNAME,
                    Constants.PREFERENCE_LOCAL_PASSWORD, R.string.settings_openhab_url_summary);
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
            initPreferences(Constants.PREFERENCE_REMOTE_URL, Constants.PREFERENCE_REMOTE_USERNAME,
                    Constants.PREFERENCE_REMOTE_PASSWORD, R.string.settings_openhab_alturl_summary);
        }
    }
}

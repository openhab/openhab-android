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
import android.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import java.text.DateFormat;

import org.openhab.habdroid.BuildConfig;
import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.text.DateFormat;

/**
 * This is a class to provide preferences activity for application.
 */

public class OpenHABPreferencesActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_prefs);

        Toolbar toolbar = (Toolbar) findViewById(R.id.openhab_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.prefs_container, new SettingsFragment())
                    .commit();
        }

        setResult(RESULT_OK);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else {
            getFragmentManager().popBackStack();
        }
    }

    public void openSubScreen(SettingsFragment subScreenFragment) {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.prefs_container, subScreenFragment)
                .addToBackStack(null)
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            updateAndInitPreferences();
        }

        @Override
        public void onStart() {
            super.onStart();

            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getTitle());
        }

        protected String getTitle() {
            return getString(R.string.action_settings);
        }

        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.preferences);

            final Preference subScreenLocalConn = getPreferenceScreen().findPreference(Constants.SUBSCREEN_LOCAL_CONNECTION);
            final Preference subScreenRemoteConn = getPreferenceScreen().findPreference(Constants.SUBSCREEN_REMOTE_CONNECTION);
            final Preference subScreenSsl = getPreferenceScreen().findPreference(Constants.SUBSCREEN_SSL_SETTINGS);
            subScreenLocalConn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ((OpenHABPreferencesActivity)getActivity()).openSubScreen(new LocalConnectionSettingsFragment());
                    return false;
                }
            });

            subScreenRemoteConn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ((OpenHABPreferencesActivity)getActivity()).openSubScreen(new RemoteConnectionSettingsFragment());
                    return false;
                }
            });

            subScreenSsl.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ((OpenHABPreferencesActivity)getActivity()).openSubScreen(new SslSettingsFragment());
                    return false;
                }
            });

            //fullscreen is not supoorted in builds < 4.4
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getPreferenceScreen().removePreference(getPreferenceScreen().findPreference(Constants.PREFERENCE_FULLSCREEN));
            }
        }

        protected String getPreferenceString(Preference preference, String defValue) {
            return getPreferenceString(preference.getKey(), defValue);
        }

        protected String getPreferenceString(String prefKey, String defValue) {
            return getPreferenceScreen().getSharedPreferences().getString(prefKey, defValue);
        }

        private void updateTextPreferenceSummary(Preference textPreference, @StringRes int summaryFormatResId,
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
    }

}

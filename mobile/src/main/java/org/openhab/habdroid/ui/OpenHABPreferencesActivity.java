/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.security.keystore.KeyProperties;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.loopj.android.image.WebImageCache;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.MyWebImage;
import org.openhab.habdroid.util.Util;

import java.security.cert.X509Certificate;

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
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            initEditorPreference(Constants.PREFERENCE_URL, R.string.settings_openhab_url_summary, false);
            initEditorPreference(Constants.PREFERENCE_ALTURL, R.string.settings_openhab_alturl_summary, false);
            initEditorPreference(Constants.PREFERENCE_USERNAME, 0, false);
            initEditorPreference(Constants.PREFERENCE_PASSWORD, 0, true);

            final Preference sslClientCert = getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT);
            final Preference sslClientCertHowTo = getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT_HOWTO);
            final Preference altUrlPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_ALTURL);
            final Preference clearCachePreference = getPreferenceScreen().findPreference(Constants
                    .PREFERENCE_CLEAR_CACHE);

            updateSslClientCertSummary(sslClientCert);

            final KeyChainAliasCallback keyChainAliasCallback = new KeyChainAliasCallback() {
                @Override
                public void alias(String alias) {
                    sslClientCert.getSharedPreferences().edit().putString(sslClientCert.getKey(), alias).apply();
                    updateSslClientCertSummary(sslClientCert);
                }
            };

            sslClientCert.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    sslClientCert.getSharedPreferences().edit().putString(sslClientCert.getKey(), null).apply();

                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                        KeyChain.choosePrivateKeyAlias(getActivity(),
                                keyChainAliasCallback,
                                new String[]{"RSA", "DSA"},
                                null,
                                getPreferenceString(altUrlPreference, null),
                                -1, null);
                    } else {
                        KeyChain.choosePrivateKeyAlias(getActivity(),
                                keyChainAliasCallback,
                                new String[]{KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC},
                                null,
                                Uri.parse(getPreferenceString(altUrlPreference, null)),
                                null);
                    }

                    return true;
                }
            });

            sslClientCertHowTo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Uri howToUri = Uri.parse(getString(R.string.settings_openhab_sslclientcert_howto_url));
                    Intent intent = new Intent(Intent.ACTION_VIEW, howToUri);
                    if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                        startActivity(intent);
                    }
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
                    WebImageCache cache = MyWebImage.getWebImageCache();
                    if (cache != null) {
                        cache.clear();
                    }
                    // Start launch activity
                    startActivity(restartIntent);
                    // Start launch activity
                    return true;
                }
            });
            //fullscreen is not supoorted in builds < 4.4
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getPreferenceScreen().removePreference(getPreferenceScreen().findPreference(Constants.PREFERENCE_FULLSCREEN));
            }
        }

        private String getPreferenceString(Preference preference, String defValue) {
            return preference.getSharedPreferences().getString(preference.getKey(), defValue);
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

        private void initEditorPreference(String key, @StringRes final int summaryFormatResId,
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

        private void updateSslClientCertSummary(final Preference sslClientCert) {
            final String certAlias = getPreferenceString(sslClientCert, null);

            new AsyncTask<Preference, Void, X509Certificate>() {
                @Override
                protected X509Certificate doInBackground(Preference... preferences) {
                    try {
                        if (certAlias != null) {
                            X509Certificate[] certificates = KeyChain.getCertificateChain(
                                    getActivity(), certAlias);
                            if (certificates != null && certificates.length > 0) {
                                return certificates[0];
                            }
                        }
                        return null;
                    } catch (KeyChainException | InterruptedException e) {
                        e.printStackTrace();
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(X509Certificate x509Certificate) {
                    if (x509Certificate != null) {
                        sslClientCert.setSummary(x509Certificate.getSubjectDN().toString());
                    } else {
                        sslClientCert.setSummary(getString(R.string.settings_openhab_none));
                    }
                }
            }.execute(sslClientCert);
        }
    }
}

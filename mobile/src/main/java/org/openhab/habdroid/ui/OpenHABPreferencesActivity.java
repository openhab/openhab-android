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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.security.keystore.KeyProperties;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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
    private final static String TAG = OpenHABPreferencesActivity.class.getSimpleName();

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
                    .add(R.id.prefs_container, new MainSettingsFragment())
                    .commit();
        }

        setResult(RESULT_OK);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
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
    }

    public static class MainSettingsFragment extends AbstractSettingsFragment {
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
            final Preference subScreenSsl = findPreference(Constants.SUBSCREEN_SSL_SETTINGS);
            final Preference themePreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_THEME);
            final Preference clearCachePreference = getPreferenceScreen().findPreference(Constants
                    .PREFERENCE_CLEAR_CACHE);
            final Preference clearDefaultSitemapPreference = getPreferenceScreen().findPreference
                    (Constants.PREFERENCE_CLEAR_DEFAULT_SITEMAP);

            String currentDefaultSitemap = clearDefaultSitemapPreference.getSharedPreferences().getString(Constants
                    .PREFERENCE_SITEMAP_NAME, "");
            String currentDefaultSitemapLabel = clearDefaultSitemapPreference.getSharedPreferences().getString(Constants
                    .PREFERENCE_SITEMAP_LABEL, "");
            if (currentDefaultSitemap.isEmpty()) {
                onNoDefaultSitemap(clearDefaultSitemapPreference);
            } else {
                clearDefaultSitemapPreference.setSummary(getString(R.string.settings_current_default_sitemap, currentDefaultSitemapLabel));
            }

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

            subScreenSsl.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getParentActivity().openSubScreen(new SslSettingsFragment());
                    return false;
                }
            });

            themePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Util.setActivityTheme(getActivity(), (String) newValue);
                    getActivity().recreate();
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

            clearDefaultSitemapPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences.Editor edit = preference.getSharedPreferences().edit();
                    edit.putString(Constants.PREFERENCE_SITEMAP_NAME, "");
                    edit.putString(Constants.PREFERENCE_SITEMAP_LABEL, "");
                    edit.apply();

                    onNoDefaultSitemap(preference);
                    return true;
                }
            });

            //fullscreen is not supoorted in builds < 4.4
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final PreferenceScreen ps = getPreferenceScreen();
                ps.removePreference(ps.findPreference(Constants.PREFERENCE_FULLSCREEN));
            }
        }

        private void onNoDefaultSitemap(Preference pref) {
            pref.setEnabled(false);
            pref.setSummary(R.string.settings_no_default_sitemap);
        }
    }

    public static class LocalConnectionSettingsFragment extends AbstractSettingsFragment {
        @Override
        protected @StringRes int getTitleResId() {
            return R.string.settings_openhab_connection;
        }

        @Override
        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.local_connection_preferences);

            initEditorPreference(Constants.PREFERENCE_LOCAL_URL, R.string.settings_openhab_url_summary, false);
            initEditorPreference(Constants.PREFERENCE_LOCAL_USERNAME, 0, false);
            initEditorPreference(Constants.PREFERENCE_LOCAL_PASSWORD, 0, true);
        }
    }

    public static class SslSettingsFragment extends AbstractSettingsFragment {
        @Override
        protected @StringRes int getTitleResId() {
            return R.string.settings_openhab_sslsettings;
        }

        protected void updateAndInitPreferences() {
            addPreferencesFromResource(R.xml.ssl_preferences);

            final Preference sslClientCert = findPreference(Constants.PREFERENCE_SSLCLIENTCERT);
            final Preference sslClientCertHowTo = findPreference(Constants.PREFERENCE_SSLCLIENTCERT_HOWTO);

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
                                getPreferenceString(Constants.PREFERENCE_REMOTE_URL, null),
                                -1, null);
                    } else {
                        KeyChain.choosePrivateKeyAlias(getActivity(),
                                keyChainAliasCallback,
                                new String[]{KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC},
                                null,
                                Uri.parse(getPreferenceString(Constants.PREFERENCE_REMOTE_URL, null)),
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
                        Log.e(TAG, e.getMessage(), e);
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

    public static class RemoteConnectionSettingsFragment extends AbstractSettingsFragment {
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

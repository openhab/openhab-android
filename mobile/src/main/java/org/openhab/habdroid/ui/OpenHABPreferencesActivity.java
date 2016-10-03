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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.openhab.habdroid.R;
import org.openhab.habdroid.util.Constants;
import org.openhab.habdroid.util.Util;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;

/**
 * This is a class to provide preferences activity for application.
 */

public class OpenHABPreferencesActivity extends PreferenceActivity {
	@SuppressWarnings("deprecation")

	@Override
	public void onStart() {
		super.onStart();
        GoogleAnalytics.getInstance(this).reportActivityStart(this);
	}

	@Override
	public void onStop() {
		super.onStop();
        GoogleAnalytics.getInstance(this).reportActivityStop(this);
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Util.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        Preference urlPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_URL);
        final Preference altUrlPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_ALTURL);
        Preference usernamePreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_USERNAME);
        Preference passwordPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_PASSWORD);
        Preference versionPreference = getPreferenceScreen().findPreference(Constants.PREFERENCE_APPVERSION);
        final Preference sslClientCert = getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT);
        final Preference sslClientCertHowTo = getPreferenceScreen().findPreference(Constants.PREFERENCE_SSLCLIENTCERT_HOWTO);
        urlPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.d("PreferencesActivity", "Validating new url = " + (String) newValue);
                String newUrl = (String) newValue;
                if (newUrl.length() == 0 || urlIsValid(newUrl)) {
                    updateTextPreferenceSummary(preference, (String) newValue);
                    return true;
                }
                showAlertDialog(getString(R.string.erorr_invalid_url));
                return false;
            }
        });
        updateTextPreferenceSummary(urlPreference, null);
        altUrlPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String newUrl = (String) newValue;
                if (newUrl.length() == 0 || urlIsValid(newUrl)) {
                    updateTextPreferenceSummary(preference, (String) newValue);
                    return true;
                }
                showAlertDialog(getString(R.string.erorr_invalid_url));
                return false;
            }
        });
        updateTextPreferenceSummary(altUrlPreference, null);
        usernamePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateTextPreferenceSummary(preference, (String) newValue);
                return true;
            }
        });
        updateTextPreferenceSummary(usernamePreference, null);
        passwordPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updatePasswordPreferenceSummary(preference, (String) newValue);
                return true;
            }
        });
        updatePasswordPreferenceSummary(passwordPreference, null);
        updateTextPreferenceSummary(versionPreference, null);


        updateSslCleintCertSumary(sslClientCert);

        final KeyChainAliasCallback keyChainAliasCallback = new KeyChainAliasCallback() {

            @Override
            public void alias(String alias) {
                sslClientCert.getSharedPreferences().edit().putString(sslClientCert.getKey(), alias).apply();
                updateSslCleintCertSumary(sslClientCert);
            }
        };

        sslClientCert.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {


                sslClientCert.getSharedPreferences().edit().putString(sslClientCert.getKey(), null).apply();

                KeyChain.choosePrivateKeyAlias(OpenHABPreferencesActivity.this,
                        keyChainAliasCallback,
                        new String[]{"RSA", "DSA"},
                        null,
                        getPreferenceString(altUrlPreference, null),
                        -1, null);

                return true;
            }
        });

        sslClientCertHowTo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                Uri hotToUri = Uri.parse(getString(R.string.settings_openhab_sslclientcert_howto_url));
                Intent intent = new Intent(Intent.ACTION_VIEW, hotToUri);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
                return true;
            }
        });
        //fullscreen is not supoorted in builds < 4.4
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getPreferenceScreen().removePreference(getPreferenceScreen().findPreference(Constants.PREFERENCE_FULLSCREEN));
        }

        setResult(RESULT_OK);
    }


    private String getPreferenceString(Preference preference, String defValue) {
        return preference.getSharedPreferences().getString(preference.getKey(), defValue);
    }

    private void updateTextPreferenceSummary(Preference textPreference, String newValue) {
        if (newValue == null) {
            if (textPreference.getSharedPreferences().getString(textPreference.getKey(), "").length() > 0)
                textPreference.setSummary(textPreference.getSharedPreferences().getString(textPreference.getKey(), ""));
            else
                textPreference.setSummary(this.getResources().getString(R.string.info_not_set));
        } else {
            if (newValue.length() > 0)
                textPreference.setSummary(newValue);
            else
                textPreference.setSummary(this.getResources().getString(R.string.info_not_set));
        }
    }

    private void updatePasswordPreferenceSummary(Preference passwordPreference, String newValue) {
        if (newValue == null) {
            if (passwordPreference.getSharedPreferences().getString(passwordPreference.getKey(), "").length() > 0)
                passwordPreference.setSummary("******");
            else
                passwordPreference.setSummary(this.getResources().getString(R.string.info_not_set));
        } else {
            if (newValue.length() > 0)
                passwordPreference.setSummary("******");
            else
                passwordPreference.setSummary(this.getResources().getString(R.string.info_not_set));
        }
    }

    private void updateSslCleintCertSumary(final Preference sslClientCert) {

        final String certAlias = getPreferenceString(sslClientCert, null);

        new AsyncTask<Preference, Void, X509Certificate>() {

            @Override
            protected X509Certificate doInBackground(Preference... preferences) {

                try {
                    if (certAlias != null) {
                        X509Certificate[] certificates = KeyChain.getCertificateChain(
                                OpenHABPreferencesActivity.this, certAlias);
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

    private boolean urlIsValid(String url) {
        // As we accept an empty URL, which means it is not configured, length==0 is ok
        if (url.length() == 0)
            return true;
        if (url.contains("\n") || url.contains(" "))
            return false;
        try {
            URL testURL = new URL(url);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }

    private void showAlertDialog(String alertMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(OpenHABPreferencesActivity.this);
        builder.setMessage(alertMessage)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void finish() {
        super.finish();
        Util.overridePendingTransition(this, true);
    }
}
